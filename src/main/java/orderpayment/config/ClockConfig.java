package orderpayment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    // Opening hours are local wall-clock times, so "now" must be read in the restaurants' time zone,
    // not whatever zone the server happens to run in. A Clock bean also lets tests fix the time.
    @Bean
    public Clock clock(@Value("${app.timezone:Asia/Bangkok}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
