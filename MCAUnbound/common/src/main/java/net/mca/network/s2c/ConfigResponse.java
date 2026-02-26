package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.Config;
import net.mca.cobalt.network.Message;

import java.io.Serial;

public class ConfigResponse implements Message {
    @Serial
    private static final long serialVersionUID = -559319583580183137L;

    private final Config config;

    public ConfigResponse(Config config) {
        this.config = config;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleConfigResponse(this);
    }

    public Config getConfig() {
        return config;
    }
}
