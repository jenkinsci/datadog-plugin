package org.datadog.jenkins.plugins.datadog.util.json;

import java.util.List;

public class JsonUtils {

    public static String toJson(List<ToJson> data) {
        final StringBuilder sb = new StringBuilder("[");

        if(data != null) {
            for(int i=0; i<data.size(); i++) {
                sb.append(data.get(i).toJson());
                if(i != data.size() - 1) {
                    sb.append(",");
                }
            }
        }

        sb.append("]");
        return sb.toString();
    }
}
