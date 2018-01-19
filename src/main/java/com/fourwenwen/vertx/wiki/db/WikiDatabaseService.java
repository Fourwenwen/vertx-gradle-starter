package com.fourwenwen.vertx.wiki.db;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

import java.util.HashMap;

/**
 * @description: ProxyGen注释用于触发该服务的客户端的代理生成代码
 * @author: Wen
 * @date: create in 2018/1/16 18:09
 */
@ProxyGen
public interface WikiDatabaseService {

    /**
     * Fluent注释是可选的，但允许Fluent，其中操作可以通过返回服务实例被链接的接口。当服务从其他JVM语言中消耗时，这对于代码生成器是非常有用的。
     *
     * @param resultHandler
     * @return
     */
    @Fluent
    WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler);

    @Fluent
    WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler);

    @Fluent
    WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler);

    static WikiDatabaseService create(JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
        return new WikiDatabaseServiceImpl(dbClient, sqlQueries, readyHandler);
    }

    static WikiDatabaseService createProxy(Vertx vertx, String address) {
        return new WikiDatabaseServiceVertxEBProxy(vertx, address);
    }
}
