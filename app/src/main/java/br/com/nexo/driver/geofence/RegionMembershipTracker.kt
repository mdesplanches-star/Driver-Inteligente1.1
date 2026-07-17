package br.com.nexo.driver.geofence

import br.com.nexo.driver.location.RawPosition
import br.com.nexo.driver.location.RawPositionRepository
import br.com.nexo.driver.location.RawPositionSubscription

/**
 * Bridges the live device position (see `br.com.nexo.driver.location.RawPositionRepository`, an
 * in-memory-only channel) to region membership. Regions are read fresh from [regionStore] on
 * every position update so edits made in the regions screen apply immediately without restarting
 * tracking. Only reports membership; makes no accept/reject decision.
 */
class RegionMembershipTracker(
    private val regionStore: RegionStore,
    private val evaluator: RegionMembershipEvaluator = RegionMembershipEvaluator(),
) : AutoCloseable {
    private var subscription: RawPositionSubscription? = null

    fun start() {
        if (subscription != null) return
        subscription = RawPositionRepository.subscribe(::onPosition)
    }

    override fun close() {
        subscription?.close()
        subscription = null
        RegionMembershipRepository.update(emptyList())
    }

    private fun onPosition(position: RawPosition?) {
        if (position == null) {
            RegionMembershipRepository.update(emptyList())
            return
        }
        val regions = regionStore.load().regions
        RegionMembershipRepository.update(evaluator.evaluate(position.latitude, position.longitude, regions))
    }
}
