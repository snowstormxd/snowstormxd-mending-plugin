package com.yourname.shulkcycle;

import com.zenith.plugin.annotation.Plugin;        // adjust if your template uses a different package
import com.zenith.plugin.ZenithProxyPlugin;       // main plugin interface in the example template
import com.zenith.plugin.PluginAPI;               // gives access to registries and client

@Plugin(
    id = "shulkcycle",
    name = "ShulkCycle",
    version = "0.1.0",
    authors = {"Theo"},
    mcVersions = {"1.21", "1.21.4"}
)
public class ShulkCyclePlugin implements ZenithProxyPlugin {
    private PluginAPI api;
    private ShulkCycleModule module;

    @Override
    public void onLoad(PluginAPI pluginApi) {
        this.api = pluginApi;
        this.module = new ShulkCycleModule(pluginApi);

        // Register command and module (names may differ slightly in your template)
        pluginApi.getCommandRegistry().register(new ShulkCycleCommand(module));
        pluginApi.getModuleRegistry().register(module);

        pluginApi.getLogger().info("[ShulkerCycle] plugin loaded");
    }
}
