package ai.grant.reduxengine.extensions

import kotlinx.coroutines.channels.ReceiveChannel

fun <S> ReceiveChannel<S>.addTo(collection: MutableCollection<ReceiveChannel<S>>) {
    collection.add(this)
}
