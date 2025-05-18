package io.github.ollama4j.examples.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@SuppressWarnings("ALL")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AirlineDetail {
    public String callsign;
    public String name;
    public String country;

    public String getName() {
        return this.name;
    }

    public String getCallsign() {
        return this.callsign;
    }
}


