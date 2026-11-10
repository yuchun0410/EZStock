import { apiDelete, apiGet, apiPost, apiPut } from './client'

/** 文章列表：{ stockId?, sort: 'hot' | 'latest', page } → { items, page, size, total, totalPages } */
export function listPosts({ stockId, sort = 'latest', page = 0 } = {}) {
  return apiGet('/api/posts', { stockId, sort, page })
}

/** 按讚 → { liked, likeCount } */
export function likePost(postId) {
  return apiPost(`/api/posts/${postId}/like`)
}

/** 收回讚 → { liked, likeCount } */
export function unlikePost(postId) {
  return apiDelete(`/api/posts/${postId}/like`)
}

export function getPost(postId) {
  return apiGet(`/api/posts/${postId}`)
}

/** post：{ title, content, stockIds } */
export function createPost(post) {
  return apiPost('/api/posts', post)
}

export function updatePost(postId, post) {
  return apiPut(`/api/posts/${postId}`, post)
}

export function deletePost(postId) {
  return apiDelete(`/api/posts/${postId}`)
}

/** 留言，回傳最新的文章內頁 */
export function addComment(postId, content) {
  return apiPost(`/api/posts/${postId}/comments`, { content })
}

/** 刪除留言，回傳最新的文章內頁 */
export function deleteComment(commentId) {
  return apiDelete(`/api/comments/${commentId}`)
}
