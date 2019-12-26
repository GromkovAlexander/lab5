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

import static org.asynchttpclient.Dsl.asyncHttpClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class App {


    private final static int ZERO = 0;
    private final static int ACTIVATE_PARALELLSIM = 1;
    private final static int DURATION = 10000;

    public static void main(String[] args) throws IOException {
        System.out.println("start!");
        ActorSystem system = ActorSystem.create("routes");

        ActorRef storageActor = system.actorOf(Props.create(StorageActor.class));
        final Http http = Http.get(system);
        final ActorMaterializer materializer = ActorMaterializer.create(system);

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = Flow.of(HttpRequest.class).map(
                req -> {
                    String url = req.getUri().query().get("testUrl").orElse("");
                    String count = req.getUri().query().get("count").orElse("");

                    Integer countInteger = Integer.parseInt(count);
                    Pair<String, Integer> data = new Pair<>(url, countInteger);

                    Source<Pair<String, Integer>, NotUsed> source = Source.from(Collections.singletonList(data));

                    Flow<Pair<String, Integer>, HttpResponse, NotUsed> testSink = Flow.<Pair<String, Integer>>create()
                            .map(pair ->
                                    new Pair<>(HttpRequest.create().withUri(pair.first()), pair.second()))
                            .mapAsync(ACTIVATE_PARALELLSIM, pair -> {
                                return Patterns.ask(
                                        storageActor,
                                        new SearchResult(data.first(), data.second()),
                                        Duration.ofMillis(DURATION)
                                ).thenCompose(actorAnswer -> {
                                    if ((int)actorAnswer != -1) {
                                        return CompletableFuture.completedFuture((int)actorAnswer);
                                    }

                                    Sink<CompletionStage<Long>, CompletionStage<Integer>> fold = Sink.
                                            fold(ZERO, (ac, el) -> {
                                                int testEl = (int)(ZERO + el.toCompletableFuture().get());
                                                return ac + testEl;
                                            });

                                    return Source.from(Collections.singletonList(pair))
                                            .toMat(
                                                    Flow.<Pair<HttpRequest, Integer>>create()
                                                    .mapConcat(p ->
                                                            Collections.nCopies(p.second(), p.first())
                                                    ).mapAsync(ACTIVATE_PARALELLSIM, request -> {
                                                        return CompletableFuture.supplyAsync(
                                                                () -> System.currentTimeMillis()
                                                        ).thenCompose(time ->
                                                                CompletableFuture.supplyAsync(
                                                                        () -> {
                                                                            CompletionStage<Long> whenResponse = asyncHttpClient()
                                                                                    .prepareGet(request.getUri().toString())
                                                                                    .execute()
                                                                                    .toCompletableFuture()
                                                                                    .thenCompose(
                                                                                            answerTime ->
                                                                                                    CompletableFuture.completedFuture(System.currentTimeMillis() - time)
                                                                                    );
                                                                            return whenResponse;
                                                                        }
                                                                )
                                                        );
                                                    })
                                                    .toMat(fold, Keep.right()),
                                                    Keep.right()
                                            )
                                            .run(materializer);
                                }).thenCompose(summ -> {
                                    Patterns.ask(
                                            storageActor,
                                            new AddResult(
                                                    data.first(),
                                                    data.second(),
                                                    summ
                                            ),
                                            DURATION
                                    );
                                    Double delayTime = (double) summ / (double) countInteger;
                                    return CompletableFuture.completedFuture(HttpResponse.create().withEntity("Delay " + delayTime.toString()));
                                });
                            });
                    CompletionStage<HttpResponse> result = source.via(testSink)
                            .toMat(Sink.last(), Keep.right())
                            .run(materializer);
                    return result.toCompletableFuture().get();

                }
        );

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
