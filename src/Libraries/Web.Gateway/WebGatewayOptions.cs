namespace QuantumCore.Web;

/// <summary>
/// Configuration for the web gateway, bound from the <c>WebGateway</c> configuration section.
/// </summary>
public class WebGatewayOptions
{
    public const string SectionName = "WebGateway";

    /// <summary>
    /// The gateway only starts when this is enabled. It is off by default so a server that only serves
    /// the original client is unaffected.
    /// </summary>
    public bool Enabled { get; set; }

    public string Host { get; set; } = "127.0.0.1";

    public int Port { get; set; } = 5000;

    /// <summary>
    /// Directory served at the web root. Leave empty to serve the client bundled with the gateway.
    /// Point it at a directory of your own to override the placeholder artwork without rebuilding.
    /// </summary>
    public string? StaticFilesPath { get; set; }
}
