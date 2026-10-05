"""
Freezes the current jImGui + jParser '-SNAPSHOT' builds into fixed versions.

Downloads every artifact a libGDX desktop + web game needs (following dependencies between the
two projects), rewrites the '-SNAPSHOT' versions inside each POM to the fixed versions below, and
writes the result to ./staging in standard Maven repository layout.

Usage: python mirror.py
"""
import re
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

SNAPSHOTS = "https://central.sonatype.com/repository/maven-snapshots/"
STAGING = Path(__file__).parent.parent / "vendor" / "maven"

# Fixed version each group is republished as. Bump the date when taking a newer snapshot.
FROZEN_VERSIONS = {
    "com.github.xpenatan.jImGui":  "zendo-20260914",
    "com.github.xpenatan.jParser": "zendo-20260920",
}

# Artifacts that games and zenlib depend on directly; their dependencies are followed automatically.
ROOTS = [
    # shared code
    ("com.github.xpenatan.jImGui", "imgui-core"),
    ("com.github.xpenatan.jImGui", "gdx-shared-impl"),
    ("com.github.xpenatan.jImGui", "gdx-gl-impl"),
    # desktop
    ("com.github.xpenatan.jImGui", "imgui-jni"),
    ("com.github.xpenatan.jImGui", "imgui-jni_windows_x64"),
    ("com.github.xpenatan.jImGui", "imgui-jni_linux_x64"),
    ("com.github.xpenatan.jImGui", "imgui-jni_mac_x64"),
    ("com.github.xpenatan.jImGui", "imgui-jni_mac_arm64"),
    # web
    ("com.github.xpenatan.jImGui", "imgui-web"),
    ("com.github.xpenatan.jImGui", "imgui-web_wasm"),
]

# Javadoc jars are skipped; sources jars are kept so the IDE can show upstream code.
WANTED_FILES = {("", "pom"), ("", "jar"), ("sources", "jar")}


def fetch(url):
    for attempt in range(1, 6):
        try:
            with urllib.request.urlopen(url, timeout=120) as response:
                return response.read()
        except Exception as error:
            print(f"    retry {attempt}/5: {url} ({error})")
            time.sleep(3 * attempt)
    sys.exit(f"FAILED to download {url}")


def rewrite_pom(pom_text):
    """Replaces '-SNAPSHOT' versions of mirrored groups with their fixed versions."""

    def rewrite_dependency(match):
        block = match.group(0)
        group = re.search(r"<groupId>(.*?)</groupId>", block).group(1)
        if group in FROZEN_VERSIONS:
            block = block.replace("<version>-SNAPSHOT</version>", f"<version>{FROZEN_VERSIONS[group]}</version>")
        return block

    pom_text = re.sub(r"<dependency>.*?</dependency>", rewrite_dependency, pom_text, flags=re.S)

    # What is left is the artifact's own version
    own_group = re.search(r"<groupId>(.*?)</groupId>", pom_text).group(1)
    pom_text = pom_text.replace("<version>-SNAPSHOT</version>", f"<version>{FROZEN_VERSIONS[own_group]}</version>", 1)

    # Without this marker Gradle reads the POM instead of looking for a '.module' file we don't mirror
    pom_text = re.sub(r"\s*<!--.*?published-with-gradle-metadata.*?-->", "", pom_text, flags=re.S)

    if "-SNAPSHOT" in pom_text:
        sys.exit(f"POM still references a snapshot after rewriting:\n{pom_text}")
    return pom_text


def dependencies_to_follow(pom_text):
    pom_text = re.sub(r'xmlns="[^"]+"', "", pom_text, count=1)
    for dependency in ET.fromstring(pom_text).findall("./dependencies/dependency"):
        group = dependency.findtext("groupId")
        if group in FROZEN_VERSIONS:
            yield group, dependency.findtext("artifactId")


def mirror(group, artifact, manifest, done):
    if (group, artifact) in done:
        return
    done.add((group, artifact))

    fixed = FROZEN_VERSIONS[group]
    source = f"{SNAPSHOTS}{group.replace('.', '/')}/{artifact}/-SNAPSHOT/"
    target = STAGING / group.replace(".", "/") / artifact / fixed
    target.mkdir(parents=True, exist_ok=True)

    metadata = ET.fromstring(fetch(source + "maven-metadata.xml"))
    build = f"{metadata.findtext('.//timestamp')}-{metadata.findtext('.//buildNumber')}"
    print(f"{group}:{artifact}  snapshot build {build} -> {fixed}")
    manifest.append(f"{group}:{artifact}:{fixed}  <-  snapshot build {build}")

    pom_text = None
    for entry in metadata.iter("snapshotVersion"):
        classifier = entry.findtext("classifier") or ""
        extension = entry.findtext("extension")
        if (classifier, extension) not in WANTED_FILES:
            continue
        suffix = f"-{classifier}" if classifier else ""
        content = fetch(f"{source}{artifact}-{entry.findtext('value')}{suffix}.{extension}")
        if extension == "pom":
            pom_text = content.decode("utf-8")
            content = rewrite_pom(pom_text).encode("utf-8")
        (target / f"{artifact}-{fixed}{suffix}.{extension}").write_bytes(content)

    for dependency in dependencies_to_follow(pom_text):
        mirror(*dependency, manifest, done)


def main():
    manifest, done = [], set()
    for root in ROOTS:
        mirror(*root, manifest, done)
    (STAGING / "MANIFEST.txt").write_text("\n".join(sorted(manifest)) + "\n")
    print(f"\n{len(done)} artifacts staged in {STAGING}")


if __name__ == "__main__":
    main()
