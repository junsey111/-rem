type Member = { roomId: string; userId: number; username: string; hostId: number };
export class RoomPresence {
  private members = new Map<string, Member>();
  private entries = new Map<string, { id: string; user_id: number; username: string; created_at: string }[]>();
  join(socketId: string, member: Member) {
    const existing = this.members.get(socketId);
    if (existing?.roomId === member.roomId && existing.userId === member.userId) { this.members.set(socketId, member); return; }
    this.leave(socketId);
    const already = [...this.members.values()].some(row => row.roomId === member.roomId && row.userId === member.userId);
    this.members.set(socketId, member);
    if (!already && member.userId !== member.hostId) {
      const rows = this.entries.get(member.roomId) || [];
      rows.push({ id: `entry-${socketId}-${Date.now()}`, user_id: member.userId, username: member.username, created_at: new Date().toISOString() });
      this.entries.set(member.roomId, rows.slice(-30));
    }
  }
  leave(socketId: string) {
    const member = this.members.get(socketId); this.members.delete(socketId);
    if (member && ![...this.members.values()].some(row => row.roomId === member.roomId)) this.entries.delete(member.roomId);
  }
  activity(roomId: string) {
    const members = [...this.members.values()].filter(row => row.roomId === roomId && row.userId !== row.hostId);
    return { viewer_count: new Set(members.map(row => row.userId)).size,
      entries: (this.entries.get(roomId) || []).filter(row => Date.parse(row.created_at) > Date.now() - 120000) };
  }
}
export const roomPresence = new RoomPresence();
