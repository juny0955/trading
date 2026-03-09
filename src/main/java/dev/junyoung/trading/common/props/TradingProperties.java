package dev.junyoung.trading.common.props;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;

@ConfigurationProperties(prefix = "trading")
@AllArgsConstructor
@Getter
public class TradingProperties {
    private final String cash;
    private final List<String> symbols;
}
