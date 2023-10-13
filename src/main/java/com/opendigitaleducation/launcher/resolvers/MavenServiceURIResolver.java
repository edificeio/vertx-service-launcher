package com.opendigitaleducation.launcher.resolvers;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
                final List<Object> list = uriRes.list().stream().filter(e -> e != null).collect(Collectors.toList());
                if(list.isEmpty()){
                    promise.fail("Could not found uri for identifier: "+identifier);
                }else{
                    promise.complete(list.get(0).toString());
                }
            }else{
                promise.fail(uriRes.cause());
            }
            return promise.future();
        });
    }


    private Future<Buffer> queryMaven(final HttpMethod method, final HttpClient client, final String uri, final String credential){
        final HeadersMultiMap headers = new HeadersMultiMap();
        if (credential != null) {
            headers.add("Authorization", "Basic " + credential);
        }
        return client.request(new RequestOptions()
                .setAbsoluteURI(uri)
                .setHeaders(headers))
            .flatMap(HttpClientRequest::send)
            .flatMap(response -> {
                switch (response.statusCode()) {
                    case 200:
                        if(HttpMethod.HEAD.equals(method)){
                            return Future.succeededFuture(Buffer.buffer());
                        }else {
                            return response.body();
                        }
                    case 401:
                        return Future.failedFuture("Failed to authenticate to maven repo: " + uri);
                    default:
                        return Future.failedFuture("Failed to download service: " + response.statusCode() + " -- " + uri);

                }
            })
            .onFailure(th -> {
                log.error("Exception while querying maven: " + uri, th);
            });
    }

}
