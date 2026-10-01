import { useEffect, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

// Temporary privacy contact; replace here when a dedicated address is available.
const PRIVACY_EMAIL = 'hauoadzso1@gmail.com'
const contactLink = <a href={`mailto:${PRIVACY_EMAIL}`}>{PRIVACY_EMAIL}</a>

const sections: { id: string; title: string; content: ReactNode }[] = [
  {
    id: 'introduction', title: 'Introduction', content: <>
      <p>Postiva is a social media content management platform that helps users connect supported social media accounts, create content, manage posts, and publish content to connected accounts.</p>
      <p>This Privacy Policy explains how Postiva collects, uses, stores, and protects information when users use the Postiva platform.</p>
    </>,
  },
  {
    id: 'information', title: 'Information We Collect', content: <>
      <p>Postiva may collect the following categories of information.</p>
      <h3>Account information</h3>
      <p>When you create a Postiva account, we collect your name, email address, and authentication information required to access Postiva. Passwords are stored as hashes, not in plaintext.</p>
      <h3>Social media account information</h3>
      <p>When you connect Facebook, Instagram, or Threads through OAuth, Postiva may receive information you authorize the provider to share. Depending on the provider and permissions granted, this may include:</p>
      <ul>
        <li>Provider user/account ID and Page or professional account ID</li>
        <li>Page/account name and username</li>
        <li>Permissions granted to Postiva</li>
        <li>OAuth access tokens and access-token expiration information</li>
        <li>Information required to publish content to the connected account</li>
      </ul>
      <p>Postiva does not ask you to manually provide your social media passwords.</p>
      <h3>Content and activity</h3>
      <p>Postiva stores content you create or upload, publication schedules, and publishing status. Supported integrations may also retrieve posts, comments, and engagement information to display and manage your connected accounts.</p>
    </>,
  },
  {
    id: 'oauth', title: 'OAuth Access Tokens', content: <>
      <p>Postiva uses OAuth authorization provided by supported social media platforms. You are redirected to the provider to authenticate and approve requested permissions.</p>
      <p>Postiva does not receive your Facebook, Instagram, or Threads password. OAuth access tokens received by the backend are encrypted before being stored and are not intentionally exposed through the Postiva frontend.</p>
    </>,
  },
  {
    id: 'use', title: 'How We Use Information', content: <>
      <p>Postiva uses collected information to:</p>
      <ul>
        <li>Authenticate users and protect the service against unauthorized access</li>
        <li>Connect, display, and maintain authorized social media accounts</li>
        <li>Create and manage content, including supported posts, comments, and reports</li>
        <li>Publish content to accounts selected by the user and schedule content for publication</li>
        <li>Process account disconnection or provider deauthorization</li>
        <li>Provide and maintain the Postiva service</li>
      </ul>
    </>,
  },
  {
    id: 'publishing', title: 'Publishing Content', content: <>
      <p>Postiva publishes content only to social media accounts that you have connected and authorized. You may choose the account and content that Postiva will manage or publish.</p>
      <p>Postiva does not obtain permission to publish to accounts that you have not authorized through the applicable social media provider.</p>
    </>,
  },
  {
    id: 'security', title: 'Data Storage and Security', content: <>
      <p>Postiva applies technical measures intended to protect user information. OAuth access tokens are encrypted before persistent storage. Sensitive credentials, such as provider application secrets, are maintained on the server side and are not intentionally exposed to the browser.</p>
      <p>Postiva stores its sign-in session in your browser’s local storage so you can remain signed in. Social media providers manage their own sign-in sessions when you authorize a connection.</p>
      <p>No system can guarantee absolute security. Please protect access to your Postiva account.</p>
    </>,
  },
  {
    id: 'sharing', title: 'Data Sharing', content: <>
      <p>Postiva does not sell users’ personal information.</p>
      <p>Information may be transmitted to social media providers when necessary to perform actions you request, such as publishing content. Postiva may also use infrastructure or service providers necessary to operate the application.</p>
    </>,
  },
  {
    id: 'meta', title: 'Facebook, Instagram and Threads', content: <>
      <p>Postiva integrates with services provided by Meta Platforms. Use of information received from Meta APIs is limited to functionality authorized by you and subject to applicable Meta platform requirements.</p>
      <p>You may revoke Postiva’s access through your Meta, Facebook, Instagram, or Threads account settings where supported.</p>
    </>,
  },
  {
    id: 'disconnect', title: 'Disconnecting a Social Account', content: <>
      <p>You can disconnect supported social media accounts from Postiva. Disconnection removes the stored access token and marks the connection as disconnected, preventing future actions using that stored authorization.</p>
      <p>Disconnecting a social account does not automatically delete unrelated Postiva account data or content already published on the provider’s platform.</p>
    </>,
  },
  {
    id: 'deletion', title: 'Data Deletion', content: <>
      <p>To request deletion of your Postiva account data or information associated with a connected social account, contact {contactLink}.</p>
      <p>For data received through supported Meta integrations, Postiva also exposes provider data-deletion handling. This handling applies to the associated integration data; it does not automatically delete all unrelated Postiva account data.</p>
    </>,
  },
  {
    id: 'retention', title: 'Data Retention', content: <>
      <p>Postiva retains information for as long as reasonably necessary to provide the service, maintain authorized account connections, satisfy legitimate operational requirements, or comply with applicable obligations.</p>
      <p>When authorization is revoked or an account is disconnected, Postiva may remove or disable stored authorization information according to the application’s implemented behavior.</p>
    </>,
  },
  {
    id: 'choices', title: 'User Choices', content: <>
      <p>You may:</p>
      <ul>
        <li>Disconnect a connected social media account</li>
        <li>Revoke permissions through the applicable social media platform</li>
        <li>Stop using Postiva</li>
        <li>Request deletion of your Postiva data by contacting {contactLink}</li>
      </ul>
    </>,
  },
  {
    id: 'children', title: 'Children’s Privacy', content: <p>Postiva is not intended for children under the minimum age required to independently use the service under applicable law.</p>,
  },
  {
    id: 'changes', title: 'Changes to This Privacy Policy', content: <>
      <p>Postiva may update this Privacy Policy when the service or its data practices change. The latest version will be available at <a href="https://postiva.store/privacy-policy">https://postiva.store/privacy-policy</a>.</p>
    </>,
  },
  {
    id: 'contact', title: 'Contact', content: <>
      <p>For privacy-related questions or data deletion requests:</p>
      <address className="not-italic">Postiva<br />Email: {contactLink}<br />Website: <a href="https://postiva.store">https://postiva.store</a></address>
    </>,
  },
]

export function PrivacyPolicyPage() {
  useEffect(() => {
    const previousTitle = document.title
    const previousLanguage = document.documentElement.lang
    document.title = 'Privacy Policy | Postiva'
    document.documentElement.lang = 'en'
    const existing = document.querySelector<HTMLMetaElement>('meta[name="description"]')
    const description = existing ?? document.createElement('meta')
    const previousDescription = description.getAttribute('content')
    description.name = 'description'
    description.content = 'Privacy Policy for the Postiva social media management platform.'
    if (!existing) document.head.appendChild(description)
    return () => {
      document.title = previousTitle
      document.documentElement.lang = previousLanguage
      if (!existing) description.remove()
      else if (previousDescription === null) description.removeAttribute('content')
      else description.content = previousDescription
    }
  }, [])

  return (
    <div lang="en" className="min-h-svh bg-[var(--app-bg)] text-[var(--app-text)]">
      <header className="border-b border-[var(--app-border)] bg-[var(--panel-bg)]">
        <div className="mx-auto flex max-w-[960px] flex-wrap items-center justify-between gap-4 px-5 py-6 sm:px-8">
          <Link to="/" className="text-xl font-extrabold tracking-tight">Postiva</Link>
          <span className="text-sm text-[var(--muted-text)]">Privacy Policy</span>
          <Link to="/" className="text-sm font-semibold text-[var(--accent-strong)] underline underline-offset-4">Back to Postiva</Link>
        </div>
      </header>
      <main id="main-content" className="mx-auto max-w-[960px] px-5 py-10 sm:px-8 sm:py-16">
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Postiva Privacy Policy</h1>
        <div className="mt-5 space-y-1 text-sm text-[var(--muted-text)]">
          <p>Effective date: <time dateTime="2026-10-01">October 1, 2026</time></p>
          <p>Last updated: <time dateTime="2026-10-01">October 1, 2026</time></p>
        </div>
        <nav aria-label="Table of contents" className="my-10 rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-6">
          <h2 className="mb-4 text-lg font-bold">Contents</h2>
          <ol className="grid gap-x-8 gap-y-3 text-sm sm:grid-cols-2">
            {sections.map((section, index) => <li key={section.id}><a className="underline decoration-[var(--app-border)] underline-offset-4 hover:text-[var(--accent-strong)]" href={`#${section.id}`}>{index + 1}. {section.title}</a></li>)}
          </ol>
        </nav>
        <article className="space-y-10 break-words rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-6 leading-relaxed sm:p-10 [&_a]:text-[var(--accent-strong)] [&_a]:underline [&_a]:underline-offset-4 [&_h3]:font-semibold [&_li]:pl-1 [&_ul]:list-disc [&_ul]:space-y-2 [&_ul]:pl-6">
          {sections.map((section, index) => (
            <section key={section.id} id={section.id} aria-labelledby={`${section.id}-heading`} className="scroll-mt-6">
              <h2 id={`${section.id}-heading`} className="mb-4 text-xl font-bold tracking-tight">{index + 1}. {section.title}</h2>
              <div className="space-y-4">{section.content}</div>
            </section>
          ))}
        </article>
      </main>
      <footer className="mx-auto flex max-w-[960px] flex-wrap justify-between gap-4 px-5 pb-8 text-sm text-[var(--muted-text)] sm:px-8">
        <span>© 2026 Postiva</span>
        <Link to="/" className="font-semibold underline underline-offset-4">Back to Postiva</Link>
      </footer>
    </div>
  )
}
