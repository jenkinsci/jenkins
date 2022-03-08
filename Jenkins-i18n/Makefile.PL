use 5.014004;
use ExtUtils::MakeMaker;
WriteMakefile(
    NAME => 'Jenkins::i18n',
    VERSION_FROM => 'lib/Jenkins/i18n.pm', # finds $VERSION, requires EU::MM from perl >= 5.5
    ABSTRACT_FROM => 'lib/Jenkins/i18n.pm',    # retrieve abstract from module
    AUTHOR        => 'Alceu Rodrigue de Freitas Junior',
    PREREQ_PM     => {
        'Config::Properties' => 1.80,
        'Set::Tiny'          => 0.04,
    },
    TEST_REQUIRES => {
        'Test::TempDir::Tiny' => 0.018,
        'Test::Exception'     => 0.43,
    },
    LICENSE          => 'mit',
    MIN_PERL_VERSION => 5.014004
);
