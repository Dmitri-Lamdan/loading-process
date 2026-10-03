# Layout Structure

## Root
- `<Box>` container with padding
- `<Typography>` header: "Loading Process – Objects Manager"

## Filter Bar
- `<Select>`: Filter Type
- `<Select>`: Filter Status
- `<TextField>`: Search
- `<Checkbox>` with visible label `Actually`: unchecked uses the existing object flow; checked loads all objects with `status=active` from `GET /v1/objects/actual`. No date, current-value, type, or service-task-status cutoff is applied.
- `<Button>`: + Add Object

## Actually mode
- The checkbox is part of the main form, not the Add Object dialog.
- While checked, the main list and details use the actual-object API response. Each returned object's details and nested data are displayed read-only; Add, edit, delete, and related-data changes are disabled.
- While unchecked, the existing API and business logic remain unchanged.

## Main Grid
- Left column (xs=4): Objects List
- Right column (xs=8): Object Details
