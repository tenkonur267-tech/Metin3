using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;

namespace QuantumCore.Web;

public static class ServiceExtensions
{
    /// <summary>
    /// Registers the browser gateway. It only starts when <c>WebGateway:Enabled</c> is set, so adding it
    /// to a host does not change how that host behaves by default.
    /// </summary>
    public static IServiceCollection AddWebGateway(this IServiceCollection services,
        IConfiguration configuration)
    {
        services.Configure<WebGatewayOptions>(configuration.GetSection(WebGatewayOptions.SectionName));
        services.AddHostedService<WebGatewayHostedService>();
        return services;
    }
}
