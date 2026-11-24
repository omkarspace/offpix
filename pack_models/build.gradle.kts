plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName.set("pack_models")
    dynamicDelivery {
        // Installed automatically with the app so 100% of AI features work
        // in Airplane Mode from first launch with zero INTERNET permission.
        deliveryType.set("install-time")
    }
}
