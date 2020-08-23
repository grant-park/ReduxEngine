package ai.grant.reduxengine.core

fun interface Reducer<S : State> {
    fun reduce(action: Action, state: S): S
}
