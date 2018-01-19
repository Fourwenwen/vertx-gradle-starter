package com.fourwenwen.vertx.wiki.db;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * @description:
 * @author: Wen
 * @date: create in 2018/1/16 18:21
 */
public class WikiDatabaseServiceImpl implements WikiDatabaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseServiceImpl.class);

    private final JDBCClient dbClient;

    private final HashMap<SqlQuery, String> sqlQueries;

    WikiDatabaseServiceImpl(JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
        this.dbClient = dbClient;
        this.sqlQueries = sqlQueries;

        dbClient.getConnection(ar -> {
            if (ar.failed()) {
                LOGGER.error("Could not open a database connection", ar.cause());
                readyHandler.handle(Future.failedFuture(ar.cause()));
            } else {
                SQLConnection connection = ar.result();
                // 建表
                connection.execute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), car -> {
                    connection.close();

                    if (car.succeeded()) {
                        LOGGER.error("Database preparation error", car.cause());
                        readyHandler.handle(Future.failedFuture(car.cause()));
                    } else {
                        readyHandler.handle(Future.succeededFuture(this));
                    }
                });
            }
        });
    }

    @Override
    public WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler) {
        dbClient.query(sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
            if (res.failed()) {
                LOGGER.error("查询apppages错误。", res.cause());
                resultHandler.handle(Future.failedFuture(res.cause()));
            } else {
                JsonArray jsonArray = new JsonArray(res.result()
                        .getResults()
                        .stream()
                        .map(json -> json.getString(0))
                        .sorted()
                        .collect(Collectors.toList()));
                resultHandler.handle(Future.succeededFuture(jsonArray));
            }
        });
        return this;
    }

    @Override
    public WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler) {
        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), new JsonArray().add(name), res -> {
            if (res.failed()) {
                LOGGER.error("查询fetchPage错误。", res.cause());
                resultHandler.handle(Future.failedFuture(res.cause()));
            } else {
                JsonObject response = new JsonObject();
                ResultSet resultSet = res.result();
                if (resultSet.getNumRows() == 0) {
                    response.put("found", false);
                } else {
                    response.put("found", true);
                    JsonArray row = resultSet.getResults().get(0);
                    response.put("id", row.getString(0));
                    response.put("rawContent", row.getString(1));
                }
                resultHandler.handle(Future.succeededFuture(response));
            }
        });
        return this;
    }

    @Override
    public WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        JsonArray jsonArray = new JsonArray().add(markdown);
        dbClient.updateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), jsonArray, res -> {
            if (res.failed()) {
                LOGGER.error("创建page出错。", res.cause());
                resultHandler.handle(Future.failedFuture(res.cause()));
            } else {
                resultHandler.handle(Future.succeededFuture());
            }
        });
        return this;
    }

    @Override
    public WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        JsonArray jsonArray = new JsonArray().add(markdown).add(id);
        dbClient.updateWithParams(sqlQueries.get(SqlQuery.SAVE_PAGE), jsonArray, res -> {
            if (res.failed()) {
                LOGGER.error("修改page出错。", res.cause());
                resultHandler.handle(Future.failedFuture(res.cause()));
            } else {
                resultHandler.handle(Future.succeededFuture());
            }
        });
        return this;
    }

    @Override
    public WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler) {
        JsonArray jsonArray = new JsonArray().add(id);
        dbClient.updateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), jsonArray, res -> {
            if (res.failed()) {
                LOGGER.error("删除数据有误", res.cause());
                resultHandler.handle(Future.failedFuture(res.cause()));
            } else {
                resultHandler.handle(Future.succeededFuture());
            }
        });
        return this;
    }
}
