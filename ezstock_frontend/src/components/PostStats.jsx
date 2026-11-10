/**
 * 文章的讚數、留言數、瀏覽數（列表和內頁共用）。
 * 圖示只是輔助，旁邊的文字標籤給螢幕閱讀器念，數字才是重點。
 */
const ICONS = {
  like: 'M7 10v11H3V10h4Zm2 11h8.6a2 2 0 0 0 2-1.6l1.3-7A2 2 0 0 0 19 10h-5.4l.8-3.9A1.8 1.8 0 0 0 12.6 4L9 10v11Z',
  comment: 'M5 5h14v10H10l-5 4V5Z',
  view: 'M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Zm10 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
}

export function StatIcon({ name }) {
  return (
    <svg className="post-stat-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d={ICONS[name]} />
    </svg>
  )
}

function PostStats({ post, showLikes = true }) {
  return (
    <span className="post-stats">
      {showLikes && (
        <span className="post-stat" aria-label={`${post.likeCount} 個讚`}>
          <StatIcon name="like" />
          {post.likeCount}
        </span>
      )}
      <span className="post-stat" aria-label={`${post.commentCount} 則留言`}>
        <StatIcon name="comment" />
        {post.commentCount}
      </span>
      <span className="post-stat" aria-label={`${post.viewCount} 次瀏覽`}>
        <StatIcon name="view" />
        {post.viewCount}
      </span>
    </span>
  )
}

export default PostStats
