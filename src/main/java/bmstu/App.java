package bmstu;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.japi.Pair;
import akka.util.ByteString;

import static org.asynchttpclient.Dsl.asyncHttpClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class App {
    public static void main(String[] args) throws IOException {
        System.out.println("start!");
        ActorSystem system = ActorSystem.create("routes");
        Props props;
        ActorRef storageActor = system.actorOf(Props.create(StorageActor.class));
        final Http http = Http.get(system);
        final ActorMaterializer materializer = ActorMaterializer.create(system);

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = Flow.of(HttpRequest.class).map(
                req -> {
                    String url = String.valueOf(req.getUri().query().get("testUrl"));
                    String count = String.valueOf(req.getUri().query().get("count"));

                    Integer countInteger = Integer.parseInt(count);
                    Pair<String, Integer> data = new Pair<>(url, countInteger);

                    Source<Pair<String, Integer>, NotUsed> source = Source.from(Collections.singletonList(data));

                    Flow<Pair<String, Integer>, HttpResponse, NotUsed> testSink = Flow.<Pair<String, Integer>>create()
                            .map(pair ->
                                    new Pair<>(HttpRequest.create().withUri(pair.first()), pair.second()))
                            .mapAsync(1, pair -> {
                                Patterns.ask(
                                        storageActor,
                                        new SearchResult(data.first(), data.second()),
                                        Duration.ofMillis(5000)
                                ).thenCompose(answer -> {
                                    if ((Integer)answer != -1) {
                                        return CompletableFuture.completedFuture((Integer)answer);
                                    }

                                    

                                    return Source.from(Collections.singletonList(pair))
                                            .toMat(
                                                    Flow.<Pair<HttpRequest, Integer>>create()
                                                    .mapConcat(p ->
                                                            Collections.nCopies(p.second(), p.first())
                                                    ).mapAsync(1, request -> {
                                                        return CompletableFuture.supplyAsync(
                                                                () -> System.currentTimeMillis()
                                                        ).thenCompose(time ->
                                                                CompletableFuture.supplyAsync(
                                                                        () -> {
                                                                            return asyncHttpClient()
                                                                                    .prepareGet(request.getUri().toString())
                                                                                    .execute()
                                                                                    .toCompletableFuture()
                                                                                    .thenCompose(
                                                                                            answerTime ->
                                                                                                    CompletableFuture.completedFuture(System.currentTimeMillis() - time)
                                                                                    );
                                                                        }
                                                                )
                                                        );
                                                    })
                                                    .toMat()
                                            )

                                })
                            })

                }
        )

        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost("localhost", 8080),
                materializer
        );
        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read();
        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate()); // and shutdown when done
    }
}
