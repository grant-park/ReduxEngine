# ReduxEngine
A multi-threaded Redux implementation in Kotlin.  

## Install
```groovy
implementation 'ai.grant:reduxengine:1.0.0'
```

## Usage
The following usage instructions include code from the [sample project](https://github.com/grant-park/ReduxEngineSampleProject) as a reference.

In your main application:

```kotlin
override fun onCreate() {
    super.onCreate()
    initialize(
        reducers = listOf(NotesReducer(), NavigationReducer()),
        epics = listOf(NotePersistenceEpic(), NetworkingEpic()),
        initialState = AppState()
    )
}
```

In your views:

```kotlin
button.setOnClickListener {
    dispatch(NavigationAction.GoToNewNote)
}

listen<AppState> {
    adapter.updateNotes(it.notes)
}
```

Requires implementing `State`, `Reducer`, `Epic`, and `Action`. By default, reducers run on the main thread and epics run on the IO thread.

## License

Copyright 2020 Grant Park

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## Sample Project
See [sample project](https://github.com/grant-park/ReduxEngineSampleProject).  
![](https://github.com/grant-park/ReduxEngineSampleProject/raw/master/demo.gif)
