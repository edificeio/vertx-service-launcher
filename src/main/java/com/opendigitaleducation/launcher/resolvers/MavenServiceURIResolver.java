package com.opendigitaleducation.launcher.resolvers;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static com.opendigitaleducation.launcher.utils.DefaultAsyncResult.handleAsyncError;

public class MavenServiceURIResolver extends MavenServiceResolver implements ServiceURIResolver {
    static Logger log = LoggerFactory.getLogger(MavenServiceURIResolver.class);

    @Override
    public void resolveURI(String identifier, Handler<AsyncResult<String>> handler) {
        initExtraRepoIfNeeded();
        final String [] id = identifier.split("~");
        if (id.length != 3) {
            handleAsyncError(new NotFoundServiceException("invalid.identifier"), handler);
            return;
        }
        String path = id[0].replaceAll("\\.", "/") + "/" + id[1] + "/" + id[2] + "/";
        if (id[2].endsWith("-SNAPSHOT")) {
            path += MAVEN_METADATA_XML;
            resolveURI(identifier, path, snapshotsRepositories, snapshotsClients).onComplete(handler);
        } else {
            final String ext = getExtensionForId(identifier);
            path += id[1] + "-" + id[2] + ext;
            resolveURI(identifier, path, releasesRepositories, releasesClients).onComplete(handler);
        }
    }

    private Future<String> resolveURI(final String identifier, final String path, final JsonArray repositories, final List<HttpClient> clients) {
        final List<Future> promises = new ArrayList<>();
        int index = 0;
        for(final Object repo : repositories){
            final Promise promise = Promise.promise();
            try {
                final String repository = ((JsonObject)repo).getString("uri");
                final String credential = ((JsonObject)repo).getString("credential");
                final HttpClient client = (index < clients.size())? clients.get(index) : createClient(repository, clients);
                final String uri = repository + path;
                if (uri.endsWith(MAVEN_METADATA_XML)) {
                    queryMaven(HttpMethod.GET,client, uri, credential).compose(buffer->{
                        try {
                            final String snapshotUri = getSnapshotPath(buffer.toString(), uri.replaceFirst(MAVEN_METADATA_XML, ""), identifier);
                            return queryMaven(HttpMethod.HEAD,client, snapshotUri, credential).map(e->{
                                return snapshotUri;
                            });
                        } catch (Exception e) {
                            return Future.failedFuture(e);
                        }
                    }).onComplete(promise);
                }else{
                    queryMaven(HttpMethod.HEAD,client, uri, credential).map(e->{
                        return uri;
                    }).onComplete(promise);
                }
                promises.add(promise.future());
                index++;
            } catch (Exception e) {
                promise.fail(e);
            }
        }
        return CompositeFuture.any(promises).compose(uriRes->{
            final Promise<String> promise = Promise.promise();
            if(uriRes.succeeded()){
                promise.complete(uriRes.list().get(0).toString());
            }else{
                promise.fail(uriRes.cause());
            }
            return promise.future();
        });
    }


    private Future<Buffer> queryMaven(final HttpMethod method, final HttpClient client, final String uri, final String credential){
        final Promise<Buffer> promise = Promise.promise();
        final HttpClientRequest req = client.request(method,uri, resp -> {
            resp.exceptionHandler(e -> {
                log.error("Exception while querying maven: " + uri, e);
            });
            if (resp.statusCode() == 200) {
                if(HttpMethod.HEAD.equals(method)){
                    promise.complete(Buffer.buffer());
                }else{
                    resp.bodyHandler(buffer -> {
                        promise.complete(buffer);
                    });
                }
            } else {
                if (resp.statusCode() == 401) {
                    promise.fail("Failed to authenticate to maven repo: " + uri);
                } else {
                    promise.fail("Failed to download service: " + resp.statusCode() + " -- " + uri);
                }
            }
        });
        if (credential != null) {
            req.putHeader("Authorization", "Basic " + credential);
        }
        req.exceptionHandler(e->{
            promise.fail(e);
        });
        req.end();
        return promise.future();
    }

}
