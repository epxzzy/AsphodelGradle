# AsphodelGradle

Welcome to the Asphodel Gradle Template!
Here is a crash-course on what all of these source-sets and comment directives mean, then explain how to get started using this environment for mod development.

## Source-sets

`src/main` is where code that is shared for all mod loaders and versions resides.

When using this source-set be very careful to follow these rules closely:

- You must ONLY call artifacts from specifically the oldest minecraft version configured in your Gradle project, and that version's available Java version features in this source-set-
- Avoid using modloader specific code, stick to only Minecraft or common libraries that you are certain haven't changed much between versions.
- When you need to call code from newer versions or specific modloaders use the Asphodel Gradle directive comments.

Failure to respect the above guidelines may lead to code not compiling on certain versions or loaders; here be dragons!

The source-sets you can pull from are:

- `src/main` - shared by every loader and every version
- `src/overlays/<version>` - shared by all loaders of one version
- `src/overlays/<version>-<loader>` - specific to only one loader and one version

For this template, code in `src/main` compiles against "Vanilla" 1.20.1 and Java 17, the earliest version that all assemblies can share without any issues. If you modify AsphodelGradle to support an earlier version, or to drop support for 1.20.1, then update the artifact retrieval configuration to match.

## Declaring versions and loaders

Each `asphodelSettings/<version>/<loader>/build.gradle` declares the loader and version it targets with the `asphodel` block:

    asphodel {
        loader = 'fabric'
        version = '1.21.1'
    }

The plugin derives a property key from the version string by replacing dots and dashes with underscores (`1.21.1` becomes `1_21_1`, `26.1` becomes `26_1`), then looks up `mc_<key>`, `java_<key>`, `minecraft_range_<key>`, and any loader-specific properties (`fabric_loader_<key>`, `forge_<key>`, `forge_range_<key>`, `neoforge_<key>`, `neoforge_range_<key>`) from `gradle.properties`. Adding support for a new version is a matter of dropping a new property block into `gradle.properties` and a new build.gradle that mentions the version; no changes to the plugin itself are required.

The shared root subproject is named by the `asphodel_shared_root` property in `gradle.properties`. Every other subproject adds it as a `compileOnly` dependency, which is how shared `src/main` sources are visible from version and loader specific overlays without being duplicated into each output.

## The directive system

Asphodel gives you two tools to mediate work between versions and loaders: comment directives that rewrite whole source at build time, and conditional patch tags that let you include or remove arbitrary lines based on the current target.

The first and largest is `asphodel::merge`, which merges code from another class into the class that carries the directive. The second and more precise way of modifying code is `asphodel::patch`, which activates named tags that you attach to individual lines as suffix markers. Both directives share the same condition syntax and the same loader and version pattern matching, described further in more detail below.

### asphodel::merge

`asphodel::merge` merges code from another class into this one at build time. The general syntax is:

    // asphodel::merge -> loader=<loader>, version=<version> -> <fully.qualified.ClassName>

* **Loader** - may be any value that a build.gradle in this project declares through its asphodel { loader = '...' } block; you are not restricted to a fixed list, since the directive simply compares against whatever loaders the template has been configured for. This particular template ships with `fabric`, `forge` and `neoforge` set up, so those are the loader values available here.
* **Version** - may likewise be any value a build.gradle declares through its asphodel { version = '...' } block, with the same freedom to extend or beyond what is currently present. This particular template ships with `1.20.1` and `1.21.1` set up, so those are the version values available here.
* **Wildcard** - if you declare: `*` then it matches any loader or version regardless of what the template is configured for, which is useful when a directive should apply unconditionally.
* **Omission** - both the loader and version keys may be omitted entirely, in which case they default to `*`, and they can appear in any order relative to each other as well.

The class name on the right must be the fully qualified name of a top-level class that lives in any source-set which is compiled for the current loader and version.

Every member, that is say: method, field, constructor, nested type and initializer of the referenced 'contributor' class is copied into this 'primary' class after the merge. A member here with a matching signature is replaced by the contributed member; a member here with no counterpart is left alone; a contributed member with no counterpart is added. If the same member key appears twice with different signatures the build fails with a clear error instead of producing invalid Java.

For example, `Example.java` declares two merge directives above the package and two stub methods:

    // asphodel::merge -> version=1.20.1 -> com.example.mod.Example20
    // asphodel::merge -> version=1.21.1 -> com.example.mod.Example21
    package com.example.mod;

    public static void setup20() {} // stub
    public static void setup21() {} // stub

On a 1.20.1 build the `Example20` contribution replaces `setup20` with the real implementation and leaves `setup21` as a no-op stub. On a 1.21.1 build the opposite happens. The result is a single class that does the right thing on every platform without any runtime dispatch.

You can stack as many directives as you want above the package. Each one that matches the current loader and version contributes its members in the order the directives appear. When two matching directives contribute the same member the latter one wins, so always order them from most general to most specific.

### asphodel::exclude

Any member of a contributor can be opted out of the merge with an exclude directive placed directly above it:

    // asphodel::exclude
    public static int sharedField = 0;

You can also call exclusions on the same line as the member declaration:

    public static int sharedField = 0; // asphodel::exclude

Either form excludes the member from the merge. This is useful when the contributor needs to reference a member that already exists in the primary; the contributor declares a local copy so its own code compiles standalone, and the exclude stops the local copy from doing unwanted overwriting on the primary's side.

### asphodel::patch

Where `asphodel::merge` operates on whole members, `asphodel::patch` operates on individual lines. A patch declares a named tag with a condition set, and any line in the same file that carries a matching suffix marker is included or removed depending on whether the tag is currently active. The syntax for declaring a patch is:

    // asphodel::patch -> loader=<loader>, version=<version>, id=<tagId>

The `id` is mandatory and must be a plain Java identifier. The `loader` and `version` keys work identically to how they do with merge. An alternative second arrow form is also accepted:

    // asphodel::patch -> loader=*, version=1.21.1 -> id=patch21

Patch declarations are file-scoped. A directive declared at the top of the class applies to every marker anywhere in the file, regardless of which member the marker sits inside. Declarations are stripped from the output, so it makes no difference whether you place them next to your fields or inside a method body, just put them wherever you think reads best for your particular use-case.

### Patch Marker Suffixes

There are two marker forms that consume a patch tag.

The first is the **Include Marker**. A line that is *entirely* a comment ending in ` *<tagId>` is included as code when the tag is active, and dropped when the tag is inactive. Both single-line `//` and multi-line `/* * */` comment styles are supported, as you can see below:

    // System.out.println("I am Kasane Teto, I output only on newer versions!"); *patch21
    /* System.out.println("I am Hatsune Miku, I also only output on newer versions!"); *patch21 */

The second form is the **Remove Marker**. A line of live code that ends in `// remove*<tagId>` is kept when the tag is inactive, with the marker and its separator stripped, and dropped entirely when the tag conditions are met and it becomes active.

    System.out.println("only on 1.20.1"); // remove*patch21
    System.out.println("only on 1.20.1"); remove*patch21

Use the Include Marker when the code you want to conditionally enable is short enough to write as a comment; use the Remove Marker when you want to omit live code that may end up breaking on certain loaders or versions but still needs to be kept in a shared source-set for one reason or another.

Referencing a tagId that no `asphodel::patch` directive declares produces a build warning and the line is treated as if the tag were inactive. Declaring the same tag id twice in the same file also produces a warning and the first declaration wins.

### Mass Patch blocks

Suffixing every line with ` *<tagId>` gets tedious when you need to patch in a large lines of code. The mass block directive solves this by letting you mark an entire range of comment lines as if it belonged to a tag at once.

Open a mass block with: `// mass*<tagId> play`
Close it with: `// mass*<tagId> pause`

Everything between the two markers that is a comment is treated as patch content for that tag. When the tag is active, the comment markers are stripped and the code is emitted; when the tag is inactive, the lines are dropped. Non-comment lines inside a mass block are passed through untouched, so the block is a purely additive layer over whatever else is in the file.

Block comments are handled transparently. A `/* ... */` span that crosses multiple lines inside a mass block is unwrapped as a unit, so both of these forms work:

    // mass*patch21 play
    //      Object blockPos = pos.below();
    //      Object blockState = level.getBlockState(blockPos);
    //      if (!this.canBurn(blockState)) {
    //          ...
    //      }
    // mass*patch21 pause

    // mass*patch21 play
    /*
    *       Object blockPos = pos.below();
    *       Object blockState = level.getBlockState(blockPos);
    */
    // mass*patch21 pause

The two forms collapse to the same output because the mass block strips exactly one optional leading space after each comment marker (`//`, `/*`, and the interior `*` of a javadoc-style block). You can mix and match styles within the same block; the transformer does not care.

A few guard rails do apply though. You obviously cannot nest mass blocks. Opening a second `play` while a first is still active raises a build error naming both ids. You cannot pause with an id different from the one that is currently open. You cannot pause while a block comment is still open. And you cannot forget the matching `pause`, since the transformer raises an error at end-of-file if a mass block is still open when the source ends.

Any tag id referenced by a mass block must still be declared by an `asphodel::patch` directive somewhere in the same file. An unknown id produces a warning and the block's content is dropped on every configuration, which is the same behaviour the ordinary suffix markers used.

### asphodel::import

Where `asphodel::merge` operates on whole members and `asphodel::patch` operates on individual lines, `asphodel::import` operates on imports. It has three actions, each sharing the same condition loader and version matching syntax pattern as the other directives. To invoke an action add one of the three keywords below after the asterisk, like so: `asphodel::import*<action>`

**Add** is a full-line comment that becomes a live import when its conditions match:

    // asphodel::import*add-> version=>=1.21.1 -> net.minecraft.world.level.BlockGetter
Matching conditions emit `import net.minecraft.world.level.BlockGetter;` at the directive's indentation; non-matching conditions drop the line.

**Replace** is a suffix on a live import that swaps it for another when its conditions match:

    import net.minecraft.some.old.package.VanillaClass; // asphodel::import*replace-> version=>=1.21.1 -> net.minecraft.new.package.VanillaClass
Matching conditions replace the whole line with the new import; non-matching conditions strip the suffix and leave the original import untouched.

**Remove** is a suffix on a live import that takes it away when its conditions match:

    import net.minecraft.resources.ResourceLocation; // asphodel::import*remove-> version=>=1.21.1
Matching conditions drop the line; non-matching conditions strip the suffix and leave the import.

### Static Imports

Both **Add** and **Replace** actions support static imports and wildcards by prefixing the target with `static `:

    // asphodel::import*add-> version=>=1.21.1 -> static net.minecraft.world.level.block.Blocks.*
    // asphodel::import*add-> version=>=1.21.1 -> static net.minecraft.world.level.block.Blocks.STONE

**Replace** infers static-ness from the original line, so swapping one static import for another needs no `static ` in the target:

    import static net.minecraft.world.level.block.Blocks.STONE; // asphodel::import*replace-> version=>=1.21.1 -> net.minecraft.world.level.block.Blocks.DIRT

If the target of a **Replace** carries its own `static `, it overrides the inference. The `.*` wildcard composes with `static ` in either position.

Import directives run after `asphodel::patch`, so include markers, remove markers, and mass blocks are fully resolved before the import pass runs. A malformed **Add** is dropped with a warning; a malformed **Replace** or **Remove** leaves the original line untouched and prints a warning.

## Loader and Version pattern matching

Beyond direct matches and wildcards, `asphodel::merge`, `asphodel::patch` and `asphodel::import` all accept a set of operators on their `loader` and `version` values that let a single directive cover many targets at once.

- **Negation** - `!<value>` matches any loader or version that is *NOT* the given value. for example `loader=!forge` matches fabric, neoforge, quilt, and anything else that is not forge.
- **Greater than** - `><value>` matches any value strictly greater than the given one. `version=>1.20.1` matches versions like 1.20.5, 1.21.1, 26.3 and every later release, but not 1.20.1 itself.
- **Greater than or equal** - `>=<value>` matches the given value and anything greater. `version=>=1.21.1` matches 1.21.1, 1.21.2 and 26.3, but not anything older.
- **Less than** - `<<value>` matches any value strictly less than the given one. `version=<1.21.1` matches 1.20.1, and earlier, but not 1.21.1 itself.
- **Less than or equal** - `<=<value>` matches the given value and anything smaller. `version=<=1.21.1` matches 1.20.1 and 1.21.1, but not anything newer.

The comparison operators are primarily useful for versions. Version comparison is done segment by segment, with each segment parsed as an integer and missing trailing segments treated as zero, so `1.21.1 < 1.21.2 < 1.21.10 < 26.1` holds as expected. This lets you write a single directive that covers an entire range of future versions without having to accommodate each one manually. Here is an example:

    // asphodel::merge -> loader=!forge, version=>=1.21.1 -> com.example.mod.ModernSetup

That directive matches fabric, neoforge, quilt, or any other non-forge loader on 1.21.1, 1.21.2, 26.1, 26.2, and everything after.

The comparison operators are *technically* accepted for loaders as well, since loaders are compared with lexicographic ordering when a comparator is used... but in practice only equality, negation, and wildcards make sense there.

## Getting started

The `Example.java` class is a minimal landing point, it declares two merge directives that pull in `Example20` and `Example21`, holds a `sharedSetup` method that logs a greeting that gets patched for 'Kasane Teto' on 1.20.1 and 'Hatsune Miku' on 1.21.1, this file also leaves `setup20` and `setup21` as empty stubs for the overlay contributions to fill in. Read through this file alongside the version overlays under `src/overlays/1.20.1/java/com/example/mod/Example20.java` and `src/overlays/1.21.1/java/com/example/mod/Example21.java` to see how the merge pattern looks end-to-end, then look at the loader overlays under `src/overlays/<version>-<loader>/` to see how a loader-specific entry point wires itself into the shared class.