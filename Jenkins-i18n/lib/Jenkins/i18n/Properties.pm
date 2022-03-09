package Jenkins::i18n::Properties;

use 5.014004;
use strict;
use warnings;
use parent 'Config::Properties';

our $VERSION = '0.01';

=pod

=head1 NAME

Jenkins::i18n::Properties - a subclass of L<Config::Properties>

=head1 SYNOPSIS

  use Jenkins::i18n::Properties;

  # reading...
  open my $fh, '<', 'my_config.props'
    or die "unable to open configuration file";
  my $properties = Config::Properties->new();
  $properties->load($fh);
  $value = $properties->getProperty($key);

  # saving...
  open my $fh, '>', 'my_config.props'
    or die "unable to open configuration file for writing";
  $properties->setProperty($key, $value);
  $properties->format('%s => %s');
  $properties->store($fh, $header );

=head1 DESCRIPTION

C<Jenkins::i18n::Properties> is a subclass of L<Config::Properties> and works
pretty much the same, except regarding the C<save> method, which is overrided.

=head2 EXPORT

None by default.

=head1 METHODS

=head2 save

This is an overrided method from the parent class.

It expects to receive the following positional parameters:

=over

=item 1.

A opened file handle created with C<open>.

=item 2.

An array reference with the license content to include in the properties file.

=back

Both are required.

This method, differently from the original of the parent class, does not
include a timestamp with C<localtime>.

This method B<does not> closes the given filehand at the end of the writting.

=cut

sub save {
    my ($self, $fh, $license_ref) = @_;
    die "a file handle is a required parameter" unless ($fh);
    die "license is a required parameter" unless ($license_ref);
    die "license must be an array reference"
        unless (ref($license_ref) eq 'ARRAY');

    foreach my $line (@{$license_ref}) {
        # the license is expected to have lines starting with
        # a space and with a new line at the end
        print $fh "#$line";
    }

    $self->_save($fh);
}

1;
__END__

=head1 SEE ALSO

=over

=item *

L<Config::Properties>

=back

=head1 AUTHOR

Alceu Rodrigues de Freitas Junior, E<lt>arfreitas@cpan.orgE<gt>

=head1 COPYRIGHT AND LICENSE

The MIT License

Copyright (C) 2022 by Alceu Freitas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

=cut

