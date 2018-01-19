package com.fourwenwen.vertx.wiki.bak;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @description:
 * @author: Wen
 * @date: create in 2018/1/11 16:08
 */
public class MainVerticle_bak extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle_bak.class);

    private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
    private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
    private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
    private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
    private static final String SQL_ALL_PAGES = "select Name from Pages";
    private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";

    private JDBCClient jdbcClient;

    private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(handler.cause());
            }
        });
    }

    private Future<Void> prepareDatabase() {
        Future<Void> future = Future.future();

        // 创建一个共享的连接，在vertx实例已知的Verticle之间共享
        jdbcClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", "jdbc:hsqldb:file:db/wiki")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30));

        jdbcClient.getConnection(ar -> {
            if (ar.failed()) {
                LOGGER.error("不能打开数据库连接。", ar.cause());
                future.fail(ar.cause());
            } else {
                SQLConnection connection = ar.result();
                connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
                    connection.close();
                    if (create.failed()) {
                        LOGGER.error("Database preparation error", create.cause());
                        future.fail(ar.cause());
                    } else {
                        future.complete();
                    }
                });
            }
        });
        return future;
    }

    private Future<Void> startHttpServer() {
        Future<Void> future = Future.future();
        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);
        router.get("/").handler(this::indexHandler);
        // /wiki/:page将匹配一个请求/wiki/Hello，在这种情况下，一个page参数将可用的值Hello。
        router.get("/wiki/:page").handler(this::pageRenderingHandler);
        // 使得所有HTTP POST请求都通过第一个处理程序io.vertx.ext.web.handler.BodyHandler。该处理程序自动将HTTP请求（例如表单提交）中的正文进行解码，然后将其作为Vert.x缓冲对象进行处理
        router.post().handler(BodyHandler.create());
        router.post("/save").handler(this::pageUpdateHandler);
        router.post("/create").handler(this::pageCreateHandler);
        router.post("/delete").handler(this::pageDeletionHandler);

        server.requestHandler(router::accept)
                .listen(8080, ar -> {
                    if (ar.succeeded()) {
                        LOGGER.info("HTTP server running on port 8080");
                        future.complete();
                    } else {
                        LOGGER.error("Could not start a HTTP server", ar.cause());
                        future.fail(ar.cause());
                    }
                });
        return future;
    }

    /**
     * 首页
     *
     * @param context
     */
    private void indexHandler(RoutingContext context) {
        jdbcClient.getConnection(car -> {
            if (car.failed()) {
                context.fail(car.cause());
            } else {
                SQLConnection connection = car.result();
                connection.query(SQL_ALL_PAGES, res -> {
                    connection.close();
                    if (res.succeeded()) {
                        List<String> pages = res.result()
                                .getResults()
                                .stream()
                                .map(json -> json.getString(0))
                                .sorted()
                                .collect(Collectors.toList());

                        context.put("title", "Wiki home");
                        context.put("pages", pages);
                        templateEngine.render(context, "templates", "/index.ftl", ar -> {
                            if (ar.succeeded()) {
                                context.response().putHeader("Content-Type", "text/html");
                                context.response().end(ar.result());
                            } else {
                            }
                        });
                    } else {
                        context.fail(res.cause());
                    }
                });
            }
        });
    }

    private static final String EMPTY_PAGE_MARKDOWN =
            "# A new page\n" +
                    "\n" +
                    "Feel-free to write in Markdown!\n";

    private void pageRenderingHandler(RoutingContext context) {
        HttpServerRequest request = context.request();
        String page = request.getParam("page");

        jdbcClient.getConnection(car -> {
            SQLConnection connection = car.result();
            connection.queryWithParams(SQL_GET_PAGE, new JsonArray().add(page), rs -> {
                connection.close();
                if (rs.succeeded()) {
                    JsonArray row = rs.result().getResults()
                            .stream()
                            .findFirst()
                            .orElseGet(() -> new JsonArray().add(-1).add(EMPTY_PAGE_MARKDOWN));
                    Integer id = row.getInteger(0);
                    String rawContent = row.getString(1);

                    context.put("title", page);
                    context.put("id", id);
                    context.put("newPage", rs.result().getResults().size() == 0 ? "yes" : "no");
                    context.put("rawContent", rawContent);
                    context.put("content", Processor.process(rawContent));
                    context.put("timestamp", new Date().toString());

                    templateEngine.render(context, "templates", "/page.ftl", ar -> {
                        if (ar.succeeded()) {
                            context.response().putHeader("Content-Type", "text/html");
                            context.response().end(ar.result());
                        } else {
                            context.fail(ar.cause());
                        }
                    });
                } else {
                    context.fail(rs.cause());
                }
            });
        });
    }

    private void pageUpdateHandler(RoutingContext context) {
        String id = context.request().getParam("id");
        String title = context.request().getParam("title");
        String markdown = context.request().getParam("markdown");
        boolean newPage = "yes".equals(context.request().getParam("newPage"));

        jdbcClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                String sql = newPage ? SQL_CREATE_PAGE : SQL_SAVE_PAGE;
                JsonArray params = new JsonArray();
                if (newPage) {
                    params.add(title).add(markdown);
                } else {
                    params.add(markdown).add(id);
                }
                connection.updateWithParams(sql, params, res -> {
                    connection.close();
                    if (res.succeeded()) {
                        context.response().setStatusCode(303);
                        context.response().putHeader("Location", "/wiki/" + title);
                        context.response().end();
                    } else {
                        context.fail(res.cause());
                    }
                });
            } else {
                context.fail(car.cause());
            }
        });
    }

    private void pageCreateHandler(RoutingContext context) {
        String pageName = context.request().getParam("name");
        String location = "/wiki/" + pageName;
        if (pageName == null | pageName.isEmpty()) {
            location = "/";
        }
        HttpServerResponse response = context.response();
        response.setStatusCode(303);
        response.putHeader("Location", location);
        response.end();
    }

    private void pageDeletionHandler(RoutingContext context) {
        String id = context.request().getParam("id");
        jdbcClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.updateWithParams(SQL_DELETE_PAGE, new JsonArray().add(id), res -> {
                    connection.close();
                    if (res.succeeded()) {
                        context.response().setStatusCode(303);
                        context.response().putHeader("Location", "/");
                        context.response().end();
                    } else {
                        context.fail(res.cause());
                    }
                });
            } else {
                context.fail(car.cause());
            }
        });
    }


}
