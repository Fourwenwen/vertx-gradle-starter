package com.fourwenwen.vertx.wiki;

import com.fourwenwen.vertx.wiki.db.WikiDatabaseVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @description:
 * @author: Wen
 * @date: create in 2018/1/11 16:08
 */
public class MainVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Future<String> dbVerticleDeployment = Future.future();
        vertx.deployVerticle(new WikiDatabaseVerticle(), dbVerticleDeployment.completer());

        dbVerticleDeployment.compose(id -> {
            Future<String> httpVerticleDeployment = Future.future();
            vertx.deployVerticle("com.fourwenwen.vertx.wiki.http.HttpServerVerticle", new DeploymentOptions().setInstances(2), httpVerticleDeployment.completer());
            return httpVerticleDeployment;
        }).setHandler(ar -> {
            if (ar.succeeded()) {
                System.out.println("启动完毕");
                startFuture.complete();
            } else {
                System.out.println("启动失败");
                startFuture.fail(ar.cause());
            }
        });
    }


}
