package ai.grant.reduxengine.util

import ai.grant.reduxengine.core.Action
import ai.grant.reduxengine.core.Reducer
import ai.grant.reduxengine.core.State

class  CompositeReducer<S : State>(private val reducers: List<Reducer<S>>) : Reducer<S> {
    override fun reduce(action: Action, state: S): S
        = reducers.fold(state) { acc, reducer -> reducer.reduce(action, acc) }
}