import { apiDelete, apiGet, apiPost, apiPut } from './client'

/** 文章列表：{ stockId?, page } → { items, page, size, total, totalPages } */
export function listPosts({ stockId, page = 0 } = {}) {
  return apiGet('/api/posts', { stockId, page })
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
