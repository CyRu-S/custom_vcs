import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowUpRight, GitBranch, GitCommit } from '@phosphor-icons/react'
import type { Repository } from './model'
import { relative, short, sortedCommits, tip } from './model'
import { CopyButton } from './ui'

// Node positions come from commit parents, so the drawing follows the server history.
export function BranchGraph({
  repository: r,
  large = false,
}: {
  repository: Repository
  large?: boolean
}) {
  const commits = useMemo(
    () =>
      sortedCommits(r)
        .slice(0, large ? 18 : 7)
        .reverse(),
    [r, large],
  )
  const [selected, setSelected] = useState('')
  const scroll = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (scroll.current)
      scroll.current.scrollLeft = scroll.current.scrollWidth - scroll.current.clientWidth
  }, [r.id])
  const current = commits.find((c) => c.id === selected) ?? tip(r) ?? commits.at(-1)
  const names = [...new Set(commits.map((c) => c.branch))]
  const width = Math.max(720, commits.length * 99 + 80)
  const height = Math.max(240, names.length * 70 + 100)
  const point = (id: string) => {
    const i = commits.findIndex((c) => c.id === id)
    if (i < 0) return null
    return {
      x: 65 + (i * (width - 130)) / Math.max(1, commits.length - 1),
      y: 92 + names.indexOf(commits[i].branch) * 74,
    }
  }
  return (
    <div className={`branch-graph ${large ? 'large' : ''}`}>
      <div className="graph-legend">
        <span>
          <i className="dot blue" />
          main
        </span>
        {names
          .filter((n) => n !== 'main')
          .map((n) => (
            <span key={n}>
              <i className="dot violet" />
              {n}
            </span>
          ))}
        <span className="graph-live">
          <i />
          Snapshot history
        </span>
      </div>
      <div
        ref={scroll}
        className="graph-scroll"
        tabIndex={0}
        role="region"
        aria-label="Interactive commit graph"
      >
        <svg
          width="100%"
          viewBox={`0 0 ${width} ${height}`}
          style={{ minWidth: large ? Math.min(width, 1000) : 600 }}
          aria-label="Commit ancestry"
        >
          <defs>
            <pattern
              id={large ? 'large-dots' : 'small-dots'}
              width="18"
              height="18"
              patternUnits="userSpaceOnUse"
            >
              <circle cx="1" cy="1" r=".8" className="grid-dot" />
            </pattern>
          </defs>
          <rect
            width={width}
            height={height}
            fill={`url(#${large ? 'large-dots' : 'small-dots'})`}
          />
          <path className="graph-baseline" d={`M 30 92 H ${width - 30}`} />
          {commits.flatMap((c) =>
            c.parents.map((parent) => {
              const a = point(parent),
                b = point(c.id)
              if (!a || !b) return null
              return (
                <path
                  key={parent + c.id}
                  d={`M${a.x} ${a.y} C${(a.x + b.x) / 2} ${a.y},${(a.x + b.x) / 2} ${b.y},${b.x} ${b.y}`}
                  className={`graph-edge ${c.branch !== 'main' || a.y !== b.y ? 'violet' : ''}`}
                />
              )
            }),
          )}
          {commits.map((c) => {
            const p = point(c.id)!
            const isTip = c.id === r.branches[r.current]
            return (
              <g
                key={c.id}
                className={`graph-node ${c.branch !== 'main' ? 'violet' : ''} ${c.id === current?.id ? 'selected' : ''}`}
                role="button"
                tabIndex={0}
                aria-label={`${isTip ? 'HEAD' : ''}${short(c.id)} ${c.message}`}
                onClick={() => setSelected(c.id)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault()
                    setSelected(c.id)
                  }
                }}
              >
                <rect x={p.x - 28} y={p.y - 18} width="56" height="58" fill="transparent" />
                {isTip && (
                  <>
                    <circle cx={p.x} cy={p.y} r="19" className="node-halo" />
                    <rect
                      x={p.x - 29}
                      y={p.y - 46}
                      width="58"
                      height="22"
                      rx="5"
                      className="head-badge"
                    />
                    <text x={p.x} y={p.y - 31} textAnchor="middle" className="head-text">
                      HEAD
                    </text>
                  </>
                )}
                <circle cx={p.x} cy={p.y} r="8" className="node-circle" />
                <circle cx={p.x} cy={p.y} r="3" className="node-core" />
                <text x={p.x} y={p.y + 31} textAnchor="middle" className="node-hash">
                  {short(c.id)}
                </text>
              </g>
            )
          })}
        </svg>
      </div>
      {current && (
        <div className="graph-selection">
          <span className="commit-avatar">D</span>
          <div>
            <strong>{current.message}</strong>
            <span>
              {current.author} committed {relative(current.date)}
              <span className="selection-hash"> · {short(current.id)}</span>
            </span>
          </div>
          <CopyButton value={current.id} label="Copy commit ID" />
          <Link
            className="graph-open"
            to={`/r/${r.id}/history?commit=${current.id}`}
            aria-label="View selected commit"
          >
            <ArrowUpRight size={19} />
          </Link>
        </div>
      )}
    </div>
  )
}
export function CommitMark() {
  return <GitCommit size={18} />
}
export function BranchMark() {
  return <GitBranch size={17} />
}
