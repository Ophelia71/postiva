import type { AppPost } from './appDataApi'

export function sortPostsNewestFirst(posts: AppPost[]) {
  return [...posts].sort((left, right) => postTimeValue(right) - postTimeValue(left))
}

function postTimeValue(post: AppPost) {
  if (!post.time) {
    return 0
  }

  const value = new Date(post.time).getTime()
  return Number.isNaN(value) ? 0 : value
}
