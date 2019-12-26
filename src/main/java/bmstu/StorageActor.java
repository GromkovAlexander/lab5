package bmstu;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import javafx.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class StorageActor extends AbstractActor {

    private HashMap<String, Map<Integer, Integer>> storage = new HashMap<>();

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(
                        SearchResult.class, sr -> {
                            String url = sr.getUrl();
                            Integer count = sr.getCount();
                            if (storage.containsKey(url) && storage.get(url).containsKey(count)) {
                                getSender().tell(storage.get(url).get(count), ActorRef.noSender());
                            } else {
                                getSender().tell(-1, ActorRef.noSender());
                            }
                        }
                )
                .match(
                        AddResult.class, ar -> {
                            Map<Integer, Integer> data = storage.getOrDefault(ar.getUrl(), new HashMap<>());
                            data.put(ar.getCount(), ar.getTime());
                            storage.put(ar.getUrl(), data);
                        }
                )
                .build();
    }
}
