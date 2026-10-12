package dev.arteshow.safe2play.mod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Safe2PlayMod implements ModInitializer {
    public static final String MOD_ID = "safe2play";
    public static final Logger LOGGER =
            LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Safe2Play client mod initialized.");
    }
}
