package br.com.caelum.camel;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaEnviaPedidos {

	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub

		CamelContext context = new DefaultCamelContext();
		context.addComponent("activemq", ActiveMQComponent.activeMQComponent("tcp://localhost:61616")); //Metodo que instancia a url e passa
		//Classe anonima de rota Camel
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				
				from("file:pedidos?noop=true").
				to("activemq:queue:pedidos");
			
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
