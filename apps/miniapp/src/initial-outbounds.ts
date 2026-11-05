export function outboundTarget(out: any) {
    const match = /^《(.+?)》\s*(.*)$/.exec(out.name.trim());
    const kind = out.kind || (match ? 'CREW' : 'OTHER');
    return {
        kind,
        name: kind === 'OTHER' ? out.name.trim() : match?.[1] || out.name.trim(),
        group: kind === 'OTHER' ? '未分组' : (out.groupName ? out.groupName.trim() : match?.[2]?.trim()) || '未分组'
    };
}

export function openingDocuments(rows: any[], crews: any[] = []) {
    const groups = new Map<string, any>();
    for (const row of rows) {
        if (row.unmanaged) continue;
        for (const out of row.initial?.outbounds || []) {
            if (!(out.quantity > 0)) continue;
            const target = outboundTarget(out);
            const recipient = crews.find(c => c.id === out.crewId || !out.crewId && c.name === target.name);
            const crewId = out.crewId || recipient?.id;
            const key = JSON.stringify([target.kind, crewId || target.name, target.group, out.relatedCrewId || '']);
            const doc = groups.get(key) || { ...target, crewId, relatedCrewId: out.relatedCrewId, count: 0 };
            doc.count++;
            groups.set(key, doc);
        }
    }
    return [...groups.values()];
}
