// Used only with server-owned user IDs and validated status values.
export const approvedMomentSql = "NOT EXISTS (SELECT 1 FROM moment_review review WHERE review.moment_id = user_moment.id AND review.status != 'APPROVED')";

export function momentReviewFields(review: any) {
  return {
    review_status: review?.status || 'APPROVED',
    review_version: Number(review?.version || 0),
    review_reason: review?.reason || '',
    reviewed_by: review?.operator_id || null,
    reviewed_at: review?.reviewed_at || null,
  };
}
