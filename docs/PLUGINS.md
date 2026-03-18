# Plugin Authoring (JavaScript)

The engine is **Rhino**. Plugins are single `.js` files placed in the app’s plugin directory (shown in-app). On load, your script runs once and can register commands.

### API (v0)
- `registerCommand(id: string, title: string, handler: () => void)`
- `toast(message: string)`
- `editor.getText(): string`
- `editor.setText(text: string): void`
- `editor.replaceAll(find: string, replace: string): void`

### Example
```js
registerCommand("example.upper", "Uppercase Buffer", function () {
  editor.setText(editor.getText().toUpperCase());
});