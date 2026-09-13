# Local build notes

Notes for building QCX on a plain Linux box (Ubuntu 24.04) without Docker.

## SDK / Roslyn version

The source generator in `Core.Networking.Generators` references
`Microsoft.CodeAnalysis.CSharp` 5.6.0. An SDK whose bundled Roslyn is older than that
silently *disables* the generator (`warning CS9057`), so the generated
`IPacketSerializable` implementations are never emitted and the build fails with a wall of
`CS0311` errors in `CorePluginAPI/Extensions/PlayerExtensions.cs`.

Ubuntu's `dotnet-sdk-10.0` package (10.0.112) ships Roslyn 5.0.26 and hits exactly this.

To make the build independent of whichever Roslyn the installed SDK happens to bundle,
`src/Directory.Build.props` pins the compiler itself via `Microsoft.Net.Compilers.Toolset`.
Keep its version in sync with `Microsoft.CodeAnalysis.CSharp` in
`Core.Networking.Generators.csproj`.

## GitVersion

`Single.csproj` uses `GitVersion.MsBuild`, which needs a full clone with tags. On a shallow
or otherwise incomplete clone it fails the build. Skip it for local work:

```bash
dotnet build src/QuantumCore.slnx -p:DisableGitVersionTask=true
```

## Tests

`src/global.json` selects the Microsoft.Testing.Platform runner, so `dotnet test` must be run
**from `src/`** and be given the solution explicitly:

```bash
cd src
dotnet test --solution QuantumCore.slnx -p:DisableGitVersionTask=true
```

Running it from the repository root picks up no `global.json`, falls back to VSTest, and every
test project errors out with "Testing with VSTest target is no longer supported".

Integration tests that use Testcontainers (Redis / MySQL fixtures) require a running Docker
daemon and will fail without one. Everything else passes.
