package bmstu;

import javafx.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class AddResult {
    private Pair<String, Pair<Integer, Integer>> result;

    public AddResult(Pair<String, Pair<Integer, Integer>> result) {
        this.result = result;
    }

    public AddResult(String url, Integer count, Integer time) {
        this.result = new Pair<>(url, new Pair<>(count, time));
    }

    public String getUrl() {
        return result.getKey();
    }

    public Integer getCount() {
        return result.getValue().getKey();
    }

    public Integer getTime() {
        return result.getValue().getValue();
    }
}
