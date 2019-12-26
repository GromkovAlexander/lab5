package bmstu;

import javafx.util.Pair;

import java.util.Map;

public class SearchResult {
    private Pair<String, Integer> res;

    public SearchResult(String url, Integer count) {
        this.res = new Pair<>(url, count);
    }

    public SearchResult(Pair<String, Integer> res) {
        this.res = res;
    }

    public String getUrl() {
        return res.getKey();
    }

    public Integer getCount() {
        return res.getValue();
    }
}
