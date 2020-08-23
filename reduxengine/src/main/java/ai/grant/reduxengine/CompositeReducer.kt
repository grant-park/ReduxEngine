package ai.grant.reduxenginesampleproject.reduxengine

import ai.grant.reduxengine.Action
import ai.grant.reduxengine.Reducer
import ai.grant.reduxengine.State

class  CompositeReducer<S : State>(private val reducers: List<Reducer<S>>) : Reducer<S> {
    override fun reduce(action: Action, state: S): S
        = reducers.foldRight(state) { reducer, acc -> reducer.reduce(action, acc) }
}