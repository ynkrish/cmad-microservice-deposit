package com.mydomain.deposit;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class DepositVerticle extends AbstractVerticle{
	private static MongoClient mongoClient;
	
	@Override
	public void start() throws Exception {
		JsonObject config = new JsonObject().put("connection_string", "mongodb://localhost:27017").put("db_name",
				"accountdb");

		mongoClient = MongoClient.createShared(vertx, config);
		HttpServer server = vertx.createHttpServer();
		Router router = Router.router(vertx);

		Router restAPI = Router.router(vertx);
		restAPI.route().handler(BodyHandler.create());

		router.mountSubRouter("/api", restAPI);

		restAPI.get("/account/:accountNumber").handler(rc -> {
			rc.response().headers().add("content-type", "application/json");
			JsonObject query = new JsonObject();
			query.put("accountNumber", rc.request().getParam("accountNumber"));
			mongoClient.find("accounts", query, res -> {
				if (res.succeeded()) {
					rc.response().end(res.result().toString());
				} else {
					res.cause().printStackTrace();
					rc.response().setStatusCode(500);
					rc.response().end();
				}

			});
		});

		restAPI.post("/account/:accountNumber/money").handler(rc -> {
			ObjectMapper mapper = new ObjectMapper();
			Money moneyToAdd;
			try {
				moneyToAdd = mapper.readValue(rc.getBodyAsString(), Money.class);
				JsonObject moneyAsJson = new JsonObject(mapper.writeValueAsString(moneyToAdd));
				rc.vertx().eventBus().publish("depositInfo", moneyAsJson);
				System.out.println("Money being added: " + moneyToAdd);
				mongoClient.save("money", moneyAsJson, res -> {
					if (res.succeeded()) {
						rc.response().setStatusCode(204);
					} else {
						res.cause().printStackTrace();
						rc.response().setStatusCode(500);
					}
					rc.response().end();
				});
			} catch (Exception e) {
				e.printStackTrace();
				rc.response().setStatusCode(500);
				rc.response().end();
			}
		});

		server.requestHandler(router::accept).listen(8080);
		System.out.println("Server started on port 8080");
	}

	public static void main(String[] args) {
		ClusterManager mgr = new HazelcastClusterManager();
		VertxOptions options = new VertxOptions().setWorkerPoolSize(10).setClusterManager(mgr);;
		//Vertx vertx = Vertx.factory.vertx(options);
		
		Vertx.clusteredVertx(options, res -> {
		  if (res.succeeded()) {
		    Vertx vertx = res.result();
		    vertx.deployVerticle("com.mydomain.deposit.DepositVerticle");
		  } else {
		    // failed!
		  }
		});
	}
}
