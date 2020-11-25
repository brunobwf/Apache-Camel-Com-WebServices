package br.com.caelum.camel;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidos {

	public static void main(String[] args) throws Exception {

			//Camel Instanciado
		CamelContext context = new DefaultCamelContext();
		context.addComponent("activemq", ActiveMQComponent.activeMQComponent("tcp://localhost:61616")); //Metodo que instancia a url e passa
		//Classe anonima de rota Camel
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				
				//Esse errorHandler tira o erro da rota padrão(não repete infinito) e permite personalizada a forma de ver o erro
				//onException(Exception.class). //onException permite um tratamento para uma exceção concreta
				//onException(SAXParseException.class).  a classe SAXParse deixa o tratamento mais específico
			    //handled(true). //Remove a mensagem 'venenosa' da rota(depois das tentativas)
				errorHandler(deadLetterChannel("activemq:queue:pedidos_DLQ"). //deadLetter é uma rota para os erros , deadLetterChannel é um errorHandler que pode ser personalizado.
						//useOriginalMessage().//Usa a mensagem original do erro e não a transformada
						logExhaustedMessageHistory(true).//Exibe a mensagem de erro,caso contrário não mostraria no log
						maximumRedeliveries(3).//Nº de vezes que vai tentar reenviar a mensagem
						redeliveryDelay(2000). //Delay para cada tentativa
						onRedelivery(new Processor() { //Quando uma nova tentativa for feita esse processor é chamado
							
							@Override
							public void process(Exchange exchange) throws Exception {
								int counter  = (int)exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
								int max  = (int)exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
								System.out.println("Redelivery " + counter + "/" + max);
								
							}
						}));
				
				//Direct->Sincrono,Seda->Assincrono e não implementa persistencia ou recuperação,tudo processado na JVM
				//from("file:pedidos?delay=5s&noop=true").//Pega da pasta pedidos 
				from("activemq:queue:pedidos").
					routeId("rota-pedidos").
					to("validator:pedido.xsd"). //validator é um componente do camel para validar 
					multicast().//Envia para as duas rotas de forma separada
					 //parallelProcessing(). //Faz as subrotas serem processadas em paralelo
					 //timeout(). //Em millis pode-se definir um timeout no processamento
					to("direct:soap").//direct -> conecta rotas
					to("direct:http");
				
				from("direct:http").
				//from("seda:http"). //Desse modo o multicast seria desnecessario e trocaria o direct
					routeId("rota-http").
					setProperty("pedidoId",xpath("/pedido/id/text()")).
					setProperty("clienteId",xpath("/pedido/pagamento/email-titular/text()")).
					split().xpath("/pedido/itens/item").
					filter().xpath("/item/formato[text()='EBOOK']").
					setProperty("ebookId",xpath("/item/livro/codigo/text()")).
					marshal().xmljson().
					log("${body}").
//					setHeader("CamelFileName",simple("${file:name.noext}.json")).
//					setHeader(Exchange.FILE_NAME,simple("${file:name.noext}-${header.CamelSplitIndex}.json")).
//					setHeader(Exchange.HTTP_METHOD,HttpMethods.POST).
					setHeader(Exchange.HTTP_METHOD,HttpMethods.GET).
					setHeader(Exchange.HTTP_QUERY,simple("ebookId=${property.ebookId}&pedidoId=${property.pedidoId}&clienteId=${property.clienteId}")).
				to("http4://localhost:8081/webservices/ebook/item");
				
				from("direct:soap").
				//from("seda:soap"). //Desse modo o multicast seria desnecessario e trocaria o direct
			    //transform(body().regexReplaceAll("tipo", "tipoEntrada")). -> Neste caso, substituiremos no body da mensagem cada ocorrência tipo por tipoEntrada.
					routeId("rota-soap").
//					setBody(constant("<envelope>teste</envelope")).
					//Se quisermos usar um XSLT em outra pasta que não faz parte do classpath: -> to("xslt:file://C:\xslt\pedidos-para-soap.xslt")
				to("xslt:pedido-para-soap.xslt").//Componente de transformação template xml.XSLT (EXtensible Stylesheet Language Transformation).
					log("${body}").
					setHeader(Exchange.CONTENT_TYPE,constant("text/xml")).
				to("http4://localhost:8081/webservices/financeiro"); //mock "Simula um endpoint". Para testes
				//Contrato do serviço SOAP acessando a URL: http://localhost:8080/webservices/financeiro?wsdl
				//O SoapUI é uma ferramenta para testar serviços web. Com ela podemos ler o WSDL e criar uma mensagem SOAP
			}
			
		});

	context.start();
	
	//Enviar uma mensagem programaticamente para um rota que começa com direct
	//ProducerTemplate producer = context.createProducerTemplate();
	//producer.sendBody("direct:soap", "<pedido> ... </pedido>");
    
	Thread.sleep(20000);
	context.stop();
	}
}
