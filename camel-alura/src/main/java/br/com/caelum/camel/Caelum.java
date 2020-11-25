package br.com.caelum.camel;

import java.text.SimpleDateFormat;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.xstream.XStreamDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import com.thoughtworks.xstream.XStream;

public class Caelum {
	
	private static MysqlConnectionPoolDataSource criaDataSource() {
	    MysqlConnectionPoolDataSource mysqlDs = new MysqlConnectionPoolDataSource();
	    mysqlDs.setDatabaseName("camel");
	    mysqlDs.setServerName("localhost");
	    mysqlDs.setPort(3306);
	    mysqlDs.setUser("root");
	    mysqlDs.setPassword("root");
	    return mysqlDs;
	}

	public static void main(String[] args) throws Exception {
		//Cria um registro que recebe o DataSource do MySQL
		SimpleRegistry registro = new SimpleRegistry();
		registro.put("mysql", criaDataSource());
		
		CamelContext context = new DefaultCamelContext(registro);
		final XStream xstream = new XStream();
		//Mapea o elemento <negociacao> do XML para a classe Negociação
		xstream.alias("negociacao", Negociacao.class);
		
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				// TODO Auto-generated method stub
				from("timer://negociacoes?fixedRate=true&delay=1s&period=360s").
			      to("http4://argentumws-spring.herokuapp.com/negociacoes").
			          convertBodyTo(String.class).
			          //O método unmarshal usará o XStream e todas as negociações serão adicionadas 
			          //em uma java.util.List. Teremos como resultados um lista de negociações (List<Negociacao>).
			          unmarshal(new XStreamDataFormat(xstream)).
			          //Divide a lista de negociações
			          split(body()).
			          process(new Processor() {
			              @Override
			              public void process(Exchange exchange) throws Exception {
			                  Negociacao negociacao = exchange.getIn().getBody(Negociacao.class);
			                  exchange.setProperty("preco", negociacao.getPreco());
			                  exchange.setProperty("quantidade", negociacao.getQuantidade());
			                  String data = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(negociacao.getData().getTime());
			                  exchange.setProperty("data", data);
			              }
			            }).
			          setBody(simple("insert into negociacao(preco, quantidade, data) values (${property.preco}, ${property.quantidade}, '${property.data}')")).
			          log("${body}"). //logando o comando esql
			          delay(1000). //esperando 1s para deixar a execução mais fácil de entender
			      to("jdbc:mysql"); //usando o componente jdbc que envia o SQL para mysql
//			    setHeader(Exchange.FILE_NAME, constant("negociacoes.xml")).
//			    to("file:saida");
//			    end();
				
			}
		});
		
		context.start();
		Thread.sleep(20000);
		context.stop();

		
	}
	
}
