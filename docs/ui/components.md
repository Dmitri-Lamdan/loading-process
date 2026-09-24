# React Components

## ObjectsManager
- Root component, holds state and layout

## FilterBar
Props:
- `typeFilter`, `statusFilter`, `searchQuery`
- `onAddObject()`

## ObjectList
Props:
- `objects[]`
- `onSelectObject(object)`

## ObjectDetails
Props:
- `selectedObject`
- `activeTab`
- `onTabChange(index)`

## EventsTable
Props:
- `events[]`
