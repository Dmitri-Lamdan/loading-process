# React Components

## ObjectsManager
- Root component, holds state and layout
- Adds an `Actually` checkbox to the main form. When unchecked, keeps the current object-loading and business-logic behavior. When checked, requests the current actual-object set from `GET /v1/objects/actual`.
- In `Actually` mode, shows every returned object and its details in read-only mode; create, update, delete, and related-data actions are unavailable. Unchecking returns to the existing mode.

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
