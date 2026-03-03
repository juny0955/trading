package dev.junyoung.trading.order.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "trading")
@Component
@Getter
@Setter
public class TradingProperties {
    private List<String> symbols = new ArrayList<>();
}
