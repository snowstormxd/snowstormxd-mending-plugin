package com.yourname.shulkcycle;

import com.zenith.command.Command;      // adjust imports to match template
import com.zenith.command.CommandContext;

public class ShulkCycleCommand extends Command {
    private final ShulkCycleModule module;

    public ShulkCycleCommand(ShulkCycleModule module) {
        super("shulkcycle");
        this.module = module;
        this.setDescription("Start/stop the shulker cycling automation. Usage: /shulkcycle <start|stop|status> [x y z]");
    }

    @Override
    public void execute(CommandContext ctx) {
        String sub = ctx.getString(0, "").toLowerCase();

        switch (sub) {
            case "start":
                try {
                    // Only chest coords required: x y z
                    int x = ctx.getInt(1);
                    int y = ctx.getInt(2);
                    int z = ctx.getInt(3);
                    module.startCycle(x, y, z);
                    ctx.reply("ShulkerCycle started for chest at " + x + "," + y + "," + z);
                } catch (Exception ex) {
                    ctx.reply("Usage: /shulkcycle start <x> <y> <z>");
                }
                break;
            case "stop":
                module.stop();
                ctx.reply("ShulkerCycle stopped.");
                break;
            case "status":
                ctx.reply("Status: " + module.getStatus());
                break;
            default:
                ctx.reply("Usage: /shulkcycle <start x y z|stop|status>");
        }
    }
}
