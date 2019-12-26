package bmstu;

import javafx.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class AddResult {
    private Pair<String, Pair<Integer, Integer>> result;

    public AddResult(Pair<String, Pair<Integer, Integer>> result) {
        this.result = result;
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
