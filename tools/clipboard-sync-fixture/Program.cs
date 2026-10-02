using System.Net;
using System.Text.Json;
using CyIME.Settings;

// No system clipboard, registry, firewall or saved credentials are used by this fixture.
var clipboard = new MemoryClipboard();
await using var server = new ClipboardServer(clipboard);
await server.StartAsync(IPAddress.Loopback, 0, "cyime", "test-only-pairing-123456");
Console.WriteLine(server.Address);
while (Console.ReadLine() is { } line)
{
    using var command = JsonDocument.Parse(line);
    var root = command.RootElement;
    var op = root.GetProperty("op").GetString();
    if (op == "quit") break;
    if (op == "copy") clipboard.Copy(root.GetProperty("text").GetString()!);
    if (op == "conflict") clipboard.Conflict = root.GetProperty("value").GetBoolean();
    if (op == "directions")
    {
        server.SendEnabled = root.GetProperty("send").GetBoolean();
        server.ReceiveEnabled = root.GetProperty("receive").GetBoolean();
    }
    Console.WriteLine(JsonSerializer.Serialize(new { text = clipboard.Text, writes = clipboard.Writes }));
}

namespace CyIME.Settings
{
    internal sealed class MemoryClipboard : IClipboardPort
    {
        private readonly object gate = new();
        public string Text { get; private set; } = "电脑初始文本";
        public int Writes { get; private set; }
        private uint sequence = 1;
        public bool Conflict { get; set; }
        public void Copy(string text) { lock (gate) { Text = text; sequence++; } }
        public Task<ClipboardSnapshot> ReadAsync()
        { lock (gate) return Task.FromResult(new ClipboardSnapshot(Text, sequence)); }
        public Task<bool> TryWriteAsync(string text, uint expectedSequence)
        {
            lock (gate)
            {
                if (Conflict || expectedSequence != sequence) return Task.FromResult(false);
                Text = text; sequence++; Writes++;
                return Task.FromResult(true);
            }
        }
    }
}
