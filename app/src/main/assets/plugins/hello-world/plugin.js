// Minimal example plugin.
// Registers a command that says Hello and appends a line to the buffer.

registerCommand("hello.world", "Hello World", function () {
  toast("Hello from plugin!");
  const txt = editor.getText();
  editor.setText(txt + "\nHello, world from plugin!\n");
});