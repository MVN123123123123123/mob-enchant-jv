package com.mobenchant.mvn123123123123123.client

import net.fabricmc.api.ClientModInitializer

object MobEnchantClient : ClientModInitializer {
	override fun onInitializeClient() {
		ProjectilePredictionSystem.register()
	}
}