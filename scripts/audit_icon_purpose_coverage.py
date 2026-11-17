#!/usr/bin/env python3
"""Source-only icon audit. Does not build, render, or prove intended-purpose uniqueness.
Run from any directory: python3 scripts/audit_icon_purpose_coverage.py [--output PATH]
XML equality is a confirmed duplicate signal. XML inequality is NOT visual uniqueness.
"""
import argparse
import collections
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'app/src/main/kotlin/com/example/chat/ui'


def active_kotlin(source):
    """Drop line/nested block comments, preserving ordinary and triple-quoted strings."""
    out = []
    i = 0
    depth = 0
    quote = None
    while i < len(source):
        if depth:
            if source.startswith('/*', i):
                depth += 1
                i += 2
            elif source.startswith('*/', i):
                depth -= 1
                i += 2
            else:
                if source[i] == '\n':
                    out.append('\n')
                i += 1
        elif quote:
            if source.startswith(quote, i):
                out.append(quote)
                i += len(quote)
                quote = None
            elif quote != '"""' and source[i] == '\\':
                out.append(source[i:i + 2])
                i += 2
            else:
                out.append(source[i])
                i += 1
        elif source.startswith('//', i):
            end = source.find('\n', i)
            i = len(source) if end < 0 else end
        elif source.startswith('/*', i):
            depth = 1
            i += 2
        elif source.startswith('"""', i):
            quote = '"""'
            out.append(quote)
            i += 3
        elif source[i] in ('"', "'"):
            quote = source[i]
            out.append(quote)
            i += 1
        else:
            out.append(source[i])
            i += 1
    if depth:
        raise ValueError('Unbalanced source comment')
    return ''.join(out)


def mappings():
    result = {}
    # Renderer checks supplemental FIRST; update those after the base maps.
    for filename in ('ChatyAssetIcons.kt', 'ChatySupplementalIcons.kt'):
        source = active_kotlin((UI / 'icons' / filename).read_text())
        for theme, body in re.findall(r'ChatyIconThemeId\.(\w+) to mapOf\((.*?)\n        \)', source, re.S):
            result.setdefault(theme, {}).update(dict(re.findall(r'ChatyIconId\.(\w+) to R\.drawable\.(\w+)', body)))
    camera_source = active_kotlin((UI / 'icons/ChatyCameraPurposeArtwork.kt').read_text())
    camera_map = dict(re.findall(r'ChatyIconId\.(\w+) to R\.drawable\.(\w+)', camera_source))
    for theme in result:
        result[theme].update(camera_map)
    return result


def audit():
    themes = mappings()
    result = {'mode': 'source-only, no compilation or visual verification',
              'meaning': 'Exact XML duplicates are findings; distinct XML and IDs do not establish visual or semantic uniqueness.',
              'scope': 'Effective unselected asset-pack mappings, plus direct UI ChatyIconId references. Selected variants, procedural drawings, dynamic calls and every intended purpose still need review.',
              'asset_themes': {}}
    for theme in themes:
        groups = collections.defaultdict(list)
        missing = []
        for icon, resource in sorted(themes[theme].items()):
            path = ROOT / 'app/src/main/res/drawable' / (resource + '.xml')
            if not path.is_file():
                missing.append(resource)
                continue
            root = ET.parse(path).getroot()
            signature = tuple((node.tag, tuple(sorted(node.attrib.items()))) for node in root.iter())
            groups[signature].append({'id': icon, 'resource': resource})
        duplicates = sorted((members for members in groups.values() if len(members) > 1), key=lambda members: members[0]['id'])
        result['asset_themes'][theme] = {'mapped_ids': len(themes[theme]), 'missing_resources': missing,
                                          'identical_xml_groups': duplicates}
    references = collections.defaultdict(list)
    stock = []
    for path in sorted(UI.rglob('*.kt')):
        if path.parent.name == 'icons':
            continue
        source = active_kotlin(path.read_text())
        relative = str(path.relative_to(ROOT))
        for number, line in enumerate(source.splitlines(), 1):
            for icon in re.findall(r'ChatyIconId\.(\w+)', line):
                references[icon].append(f'{relative}:{number}')
            # Prior line-only matcher missed whitespace/newline and named vector arguments.
            # if re.search(r'\bIcon\(Icons\.', line): stock.append(...)
        for match in re.finditer(r'\bIcon\s*\(\s*(?:imageVector\s*=\s*)?Icons\.(?:\w+\.)*\w+', source):
            stock.append(f'{relative}:{source.count(chr(10), 0, match.start()) + 1}')
    result['direct_ui_reference_counts'] = {key: len(value) for key, value in sorted(references.items())}
    result['stock_icon_call_sites_requiring_purpose_review'] = stock
    result['resolved_cross_purpose_binding'] = {
        'source': 'app/src/main/kotlin/com/example/chat/ui/chats/home/HomeAppBars.kt',
        'new_call': 'callAudio', 'who_can_call_me': 'callPrivacy',
        'status': 'Distinct IDs and artwork; prior callAudio binding retained in comment.'}
    return result


def check_menu_contract():
    home = active_kotlin((UI / 'chats/ChatsHomeScreen.kt').read_text())
    menu = active_kotlin((UI / 'chats/home/HomeAppBars.kt').read_text())
    settings = active_kotlin((UI / 'settings/home/HomeScreenSettingsPage.kt').read_text())
    filters = active_kotlin((UI / 'chats/home/HomeTagFilters.kt').read_text())
    checks = {
        'triage_button_removed': 'Start unread triage' not in home and 'End unread triage' not in home,
        'old_preview_label_removed': 'Preview appearance reset' not in home,
        'appearance_callback_wired': 'onCheckHomeAppearance = { showAppearanceResetPreview = true }' in home,
        'appearance_menu_name': 'Text("Check Home appearance")' in menu,
        'unread_filter_retained': '"Unread"' in filters,
        'date_grouping_setting': '"Group chats by date"' in settings and 'HOME_GROUP_CHATS_BY_DATE,enabled' in settings,
    }
    if not all(checks.values()):
        raise AssertionError(checks)
    return checks


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = audit()
    report['recovered_menu_mapping_checks'] = check_menu_contract()
    text = json.dumps(report, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text)
    print(json.dumps({'menu_checks': report['recovered_menu_mapping_checks'],
                      'asset_duplicate_groups': {theme: len(data['identical_xml_groups']) for theme, data in report['asset_themes'].items()},
                      'stock_icon_sites_to_review': len(report['stock_icon_call_sites_requiring_purpose_review'])}, indent=2))
