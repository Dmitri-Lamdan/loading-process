# Material UI Theme Configuration

```js
const theme = createTheme({
  palette: {
    primary: { main: '#1976d2' },
    secondary: { main: '#9c27b0' },
    background: { default: '#f5f5f5' },
  },
  typography: {
    fontFamily: 'Roboto, sans-serif',
    fontSize: 14,
  },
});


---

## 🗂️ `data.md`
```markdown
# Example Data Models

### Object
```js
{
  name: "Engine Oil",
  type: "C",
  status: "planned",
  value: 123.45,
  nextService: "2026-08-01",
  created: "2026-07-10"
}

{
  user: "John",
  date: "2026-09-20",
  type: "change",
  message: "Updated value"
}


---

These Markdown files together describe **all CSS classes, component structures, and data models** needed for automatic React code generation later.

Would you like me to add one more file — `generator-spec.md` — that defines how an MCP server should transform these `.md` files into React source code?