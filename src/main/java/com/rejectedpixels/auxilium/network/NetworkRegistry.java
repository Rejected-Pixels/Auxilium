package com.rejectedpixels.auxilium.network;

import com.rejectedpixels.auxilium.Auxilium;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Auxilium.MODID)
public class NetworkRegistry {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(SetEnergyMatrixTargetPayload.TYPE, SetEnergyMatrixTargetPayload.STREAM_CODEC, SetEnergyMatrixTargetPayload::handle);
    }
}
